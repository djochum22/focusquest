//! Whether a web address is blocked. A port of the browser extension's `urlMatcher.ts` and
//! `rulePrecedence.ts`, which reproduce the backend's `TargetUrl`, `UrlRule` and `RulePrecedence`.
//!
//! `youtube.com` blocks the domain and every subdomain; `youtube.com/shorts` blocks that path and
//! everything below it, but not `/shortsfoo`. The most specific matching rule wins (more host labels,
//! then more path segments), and an allowlist rule wins a tie. Addresses are normalised toward
//! blocking: lowercased, percent-escapes decoded, repeated slashes and a trailing slash ignored.

use std::cmp::Ordering;

use percent_encoding::percent_decode_str;
use serde::Deserialize;
use tauri::Url;

/// A rule as FocusQuest sends it (`ExtensionRule`): its host, and a path prefix or nothing.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UrlRule {
    pub host: String,
    /// "" for a rule on the whole domain.
    #[serde(default, deserialize_with = "path_or_empty")]
    pub path: String,
}

fn path_or_empty<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<String, D::Error> {
    Ok(Option::<String>::deserialize(deserializer)?.unwrap_or_default())
}

impl UrlRule {
    /// The rule as the user wrote it, such as `youtube.com/shorts`.
    pub fn label(&self) -> String {
        format!("{}{}", self.host, self.path)
    }
}

/// A visited address, in the form rules are matched against.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Target {
    pub host: String,
    pub path: String,
}

/// Parses an http(s) address; None for anything else, which is never blocked.
pub fn parse(input: &str) -> Option<Target> {
    let url = Url::parse(input.trim()).ok()?;
    if url.scheme() != "http" && url.scheme() != "https" {
        return None;
    }
    let mut host = url.host_str()?.to_lowercase();
    if host.ends_with('.') {
        host.pop();
    }
    if host.is_empty() {
        return None;
    }
    // A malformed escape is kept as it is, so it can never make an address slip past a rule.
    let decoded = percent_decode_str(url.path()).decode_utf8().map(|path| path.into_owned()).unwrap_or_else(|_| url.path().to_string());
    let mut path = String::with_capacity(decoded.len());
    for c in decoded.to_lowercase().chars() {
        if !(c == '/' && path.ends_with('/')) {
            path.push(c);
        }
    }
    if path.ends_with('/') {
        path.pop();
    }
    Some(Target { host, path })
}

fn matches(rule: &UrlRule, target: &Target) -> bool {
    let host = target.host == rule.host || target.host.ends_with(&format!(".{}", rule.host));
    let path = rule.path.is_empty() || target.path == rule.path || target.path.starts_with(&format!("{}/", rule.path));
    host && path
}

fn specificity(rule: &UrlRule) -> (usize, usize) {
    (rule.host.split('.').count(), rule.path.matches('/').count())
}

fn most_specific<'a>(target: &Target, rules: &'a [UrlRule]) -> Option<&'a UrlRule> {
    rules.iter().filter(|rule| matches(rule, target)).fold(None, |best, rule| match best {
        Some(best) if specificity(rule).cmp(&specificity(best)) != Ordering::Greater => Some(best),
        _ => Some(rule),
    })
}

/// The rule that blocks this address, or None when it may load.
pub fn blocked_by<'a>(target: &Target, block: &'a [UrlRule], allow: &[UrlRule]) -> Option<&'a UrlRule> {
    let block = most_specific(target, block)?;
    match most_specific(target, allow) {
        Some(allow) if specificity(allow) >= specificity(block) => None,
        _ => Some(block),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rule(value: &str) -> UrlRule {
        match value.split_once('/') {
            Some((host, path)) => UrlRule { host: host.into(), path: format!("/{path}") },
            None => UrlRule { host: value.into(), path: String::new() },
        }
    }

    fn blocked(url: &str, block: &[&str], allow: &[&str]) -> bool {
        let block: Vec<UrlRule> = block.iter().map(|r| rule(r)).collect();
        let allow: Vec<UrlRule> = allow.iter().map(|r| rule(r)).collect();
        parse(url).is_some_and(|target| blocked_by(&target, &block, &allow).is_some())
    }

    #[test]
    fn a_domain_rule_covers_its_subdomains_but_not_lookalikes() {
        assert!(blocked("https://youtube.com/watch?v=1", &["youtube.com"], &[]));
        assert!(blocked("https://m.youtube.com/", &["youtube.com"], &[]));
        assert!(blocked("http://WWW.YouTube.com./", &["youtube.com"], &[]));
        assert!(!blocked("https://notyoutube.com/", &["youtube.com"], &[]));
    }

    #[test]
    fn a_path_rule_covers_its_path_on_a_segment_boundary() {
        assert!(blocked("https://youtube.com/shorts", &["youtube.com/shorts"], &[]));
        assert!(blocked("https://youtube.com/shorts/abc?x=1", &["youtube.com/shorts"], &[]));
        assert!(blocked("https://youtube.com/Shorts/", &["youtube.com/shorts"], &[]));
        assert!(!blocked("https://youtube.com/shortsfoo", &["youtube.com/shorts"], &[]));
        assert!(!blocked("https://youtube.com/", &["youtube.com/shorts"], &[]));
    }

    #[test]
    fn odd_spellings_do_not_dodge_a_rule() {
        assert!(blocked("https://youtube.com//shorts/abc", &["youtube.com/shorts"], &[]));
        assert!(blocked("https://youtube.com/%73horts/abc", &["youtube.com/shorts"], &[]));
        assert!(blocked("https://youtube.com/x/%2e%2e/shorts", &["youtube.com/shorts"], &[]));
    }

    #[test]
    fn the_most_specific_rule_wins_and_allow_wins_a_tie() {
        assert!(!blocked("https://example.com/docs/setup", &["example.com"], &["example.com/docs"]));
        assert!(blocked("https://example.com/forum", &["example.com"], &["example.com/docs"]));
        assert!(!blocked("https://example.com/", &["example.com"], &["example.com"]));
        assert!(blocked("https://example.com/docs/private", &["example.com/docs/private"], &["example.com/docs"]));
        assert!(!blocked("https://mail.google.com/", &["google.com"], &["mail.google.com"]));
    }

    #[test]
    fn only_web_pages_are_blocked() {
        assert!(!blocked("chrome://settings", &["settings"], &[]));
        assert!(!blocked("file:///Users/me/youtube.com", &["youtube.com"], &[]));
        assert!(!blocked("not a url", &["youtube.com"], &[]));
        assert!(!blocked("http://localhost:8080/blocked?site=youtube.com", &["youtube.com"], &[]));
    }

    #[test]
    fn rules_are_read_as_focusquest_sends_them() {
        let rules: Vec<UrlRule> = serde_json::from_str(
            r#"[{"id":1,"targetType":"DOMAIN","targetValue":"youtube.com","host":"youtube.com","path":null,"displayName":null},
                {"id":2,"targetType":"URL","targetValue":"reddit.com/r/all","host":"reddit.com","path":"/r/all","displayName":"Reddit"}]"#,
        )
        .unwrap();
        assert_eq!(rules, vec![rule("youtube.com"), rule("reddit.com/r/all")]);
        assert_eq!(rules[1].label(), "reddit.com/r/all");
    }
}
