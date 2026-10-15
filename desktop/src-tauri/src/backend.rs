//! Finds FocusQuest, or starts it.
//!
//! FocusQuest may already run on this computer (the Docker container, or `bootRun`). Then the app
//! uses it, and leaves it running on quit. Otherwise it starts its own copy: the bundled JRE and
//! boot jar (`scripts/prepare-backend.sh`), with its data in `~/Library/Application Support/FocusQuest`,
//! and stops it on quit. Either way it listens on 127.0.0.1:8080, where the browser extension and the
//! camera companion look for it.

use std::fs;
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

pub const ADDRESS: &str = "127.0.0.1:8080";
pub const URL: &str = "http://127.0.0.1:8080/";
/// Answers without signing in, so it tells FocusQuest from any other program on the port.
const PROBE: &str = "/api/auth/setup-status";
/// How long a backend that is starting may take: a first start migrates the database.
const START_TIMEOUT: Duration = Duration::from_secs(120);
/// How long it may take to stop before it is killed.
const STOP_TIMEOUT: Duration = Duration::from_secs(15);

/// What answers on [`ADDRESS`].
#[derive(Debug, PartialEq, Eq)]
pub enum Found {
    FocusQuest,
    Nothing,
    /// Something else holds the port.
    Other,
}

pub fn probe() -> Found {
    let address: SocketAddr = ADDRESS.parse().expect("a valid address");
    let Ok(mut stream) = TcpStream::connect_timeout(&address, Duration::from_millis(500)) else {
        return Found::Nothing;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_secs(3)));
    let request = format!("GET {PROBE} HTTP/1.1\r\nHost: {ADDRESS}\r\nAccept: application/json\r\nConnection: close\r\n\r\n");
    if stream.write_all(request.as_bytes()).is_err() {
        return Found::Other;
    }
    let mut response = String::new();
    let _ = stream.take(64 * 1024).read_to_string(&mut response);
    if is_focusquest(&response) {
        Found::FocusQuest
    } else {
        Found::Other
    }
}

fn is_focusquest(response: &str) -> bool {
    response.starts_with("HTTP/1.1 200") && response.contains("\"setupRequired\"")
}

/// Where the app keeps its own copy's data, and its log.
pub struct Places {
    pub data: PathBuf,
    pub log: PathBuf,
}

impl Places {
    pub fn in_home(home: &Path) -> Self {
        Places {
            data: home.join("Library/Application Support/FocusQuest"),
            log: home.join("Library/Logs/FocusQuest/backend.log"),
        }
    }
}

/// The copy of FocusQuest this app started.
pub struct Backend {
    child: Child,
}

impl Backend {
    /// Starts the bundled backend in `bundle` (holding `jre/` and `focusquest.jar`) and waits until it
    /// answers. The error is for the user.
    pub fn start(bundle: &Path, places: &Places) -> Result<Backend, String> {
        let java = bundle.join("jre/bin/java");
        let jar = bundle.join("focusquest.jar");
        if !java.exists() || !jar.exists() {
            return Err(format!(
                "This build of the app has no FocusQuest inside ({} is missing). Build it with `npm run build` in desktop/, or start FocusQuest with `docker compose up -d`.",
                if java.exists() { jar.display() } else { java.display() }
            ));
        }
        fs::create_dir_all(&places.data).map_err(|e| format!("Cannot create {}: {e}", places.data.display()))?;
        if let Some(folder) = places.log.parent() {
            fs::create_dir_all(folder).map_err(|e| format!("Cannot create {}: {e}", folder.display()))?;
        }
        let secret = jwt_secret(&places.data)?;
        let log = fs::File::create(&places.log).map_err(|e| format!("Cannot write {}: {e}", places.log.display()))?;
        let child = Command::new(&java)
            .arg("-jar")
            .arg(&jar)
            .env("FOCUSQUEST_DATA_DIR", &places.data)
            .env("FOCUSQUEST_JWT_SECRET", secret)
            .env("FOCUSQUEST_BIND", "127.0.0.1")
            .env("FOCUSQUEST_PORT", "8080")
            .current_dir(&places.data)
            .stdin(Stdio::null())
            .stdout(log.try_clone().map_err(|e| e.to_string())?)
            .stderr(log)
            .spawn()
            .map_err(|e| format!("Cannot start FocusQuest: {e}"))?;
        let mut backend = Backend { child };
        backend.wait_until_ready(&places.log)?;
        Ok(backend)
    }

    fn wait_until_ready(&mut self, log: &Path) -> Result<(), String> {
        let started = Instant::now();
        loop {
            if let Ok(Some(status)) = self.child.try_wait() {
                return Err(format!("FocusQuest stopped while starting ({status}). Its log is {}.", log.display()));
            }
            match probe() {
                Found::FocusQuest => return Ok(()),
                _ if started.elapsed() > START_TIMEOUT => {
                    self.stop();
                    return Err(format!(
                        "FocusQuest did not start within {} seconds. Its log is {}.",
                        START_TIMEOUT.as_secs(),
                        log.display()
                    ));
                }
                _ => thread::sleep(Duration::from_millis(500)),
            }
        }
    }

    /// Asks it to stop (SIGTERM, so Spring closes the database cleanly), and kills it if it does not.
    pub fn stop(&mut self) {
        if let Ok(Some(_)) = self.child.try_wait() {
            return;
        }
        let _ = Command::new("kill").arg("-TERM").arg(self.child.id().to_string()).status();
        let asked = Instant::now();
        while asked.elapsed() < STOP_TIMEOUT {
            if let Ok(Some(_)) = self.child.try_wait() {
                return;
            }
            thread::sleep(Duration::from_millis(200));
        }
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

impl Drop for Backend {
    fn drop(&mut self) {
        self.stop();
    }
}

/// The secret that signs sign-ins, kept beside the data so they last across restarts. Made once,
/// readable by this user only.
fn jwt_secret(data: &Path) -> Result<String, String> {
    let path = data.join("jwt-secret");
    if let Ok(secret) = fs::read_to_string(&path) {
        let secret = secret.trim().to_string();
        if secret.len() >= 64 {
            return Ok(secret);
        }
    }
    let mut bytes = [0u8; 48];
    fs::File::open("/dev/urandom")
        .and_then(|mut random| random.read_exact(&mut bytes))
        .map_err(|e| format!("Cannot make a secret: {e}"))?;
    let secret: String = bytes.iter().map(|byte| format!("{byte:02x}")).collect();
    write_private(&path, &secret).map_err(|e| format!("Cannot write {}: {e}", path.display()))?;
    Ok(secret)
}

fn write_private(path: &Path, contents: &str) -> std::io::Result<()> {
    use std::os::unix::fs::OpenOptionsExt;
    let mut file = fs::OpenOptions::new().write(true).create(true).truncate(true).mode(0o600).open(path)?;
    file.write_all(contents.as_bytes())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_focusquest_s_answer_counts() {
        assert!(is_focusquest("HTTP/1.1 200 \r\nContent-Type: application/json\r\n\r\n{\"setupRequired\":false}"));
        assert!(!is_focusquest("HTTP/1.1 404 \r\n\r\n{\"setupRequired\":false}"));
        assert!(!is_focusquest("HTTP/1.1 200 OK\r\n\r\n<html>another program</html>"));
    }

    #[test]
    fn the_secret_is_made_once_and_kept_private() {
        let folder = std::env::temp_dir().join(format!("focusquest-secret-{}", std::process::id()));
        fs::create_dir_all(&folder).unwrap();
        let first = jwt_secret(&folder).unwrap();
        assert_eq!(first.len(), 96);
        assert_eq!(jwt_secret(&folder).unwrap(), first);
        use std::os::unix::fs::PermissionsExt;
        assert_eq!(fs::metadata(folder.join("jwt-secret")).unwrap().permissions().mode() & 0o777, 0o600);
        fs::remove_dir_all(&folder).unwrap();
    }

    #[test]
    fn its_own_copy_lives_in_application_support() {
        let places = Places::in_home(Path::new("/Users/someone"));
        assert_eq!(places.data, Path::new("/Users/someone/Library/Application Support/FocusQuest"));
        assert_eq!(places.log, Path::new("/Users/someone/Library/Logs/FocusQuest/backend.log"));
    }
}
