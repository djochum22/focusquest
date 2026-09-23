package com.example.focusquest.blocking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Domain matching, subdomain matching, path matching, child-path matching and query handling. */
class UrlRuleMatchingTest {

    private static boolean matches(String rule, String url) {
        Optional<TargetUrl> target = TargetUrl.parse(url);
        return target.isPresent() && RuleNormalizer.normalize(rule).matches(target.get());
    }

    @Nested
    class DomainMatching {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://youtube.com",
                "https://youtube.com/",
                "https://youtube.com/watch?v=abc",
                "http://youtube.com/anything/at/all",
                "https://YOUTUBE.com/",
                "https://youtube.com./",
                "https://youtube.com:8443/watch",
                "https://user:pass@youtube.com/"
        })
        void domainRuleMatchesTheDomainItself(String url) {
            assertThat(matches("youtube.com", url)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://www.youtube.com/watch",
                "https://m.youtube.com/",
                "https://a.b.youtube.com/"
        })
        void domainRuleMatchesSubdomains(String url) {
            assertThat(matches("youtube.com", url)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://notyoutube.com/",
                "https://youtube.com.evil.example/",
                "https://youtube.co/",
                "https://example.com/youtube.com",
                "https://example.com/?next=youtube.com"
        })
        void domainRuleDoesNotMatchLookalikeHosts(String url) {
            assertThat(matches("youtube.com", url)).isFalse();
        }

        @Test
        void subdomainRuleDoesNotMatchTheParentDomainOrSiblings() {
            assertThat(matches("old.reddit.com", "https://old.reddit.com/r/all")).isTrue();
            assertThat(matches("old.reddit.com", "https://reddit.com/r/all")).isFalse();
            assertThat(matches("old.reddit.com", "https://www.reddit.com/r/all")).isFalse();
            assertThat(matches("old.reddit.com", "https://x.old.reddit.com/")).isTrue();
        }
    }

    @Nested
    class PathMatching {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://youtube.com/shorts",
                "https://youtube.com/shorts/",
                "https://www.youtube.com/shorts",
                "https://m.youtube.com/shorts"
        })
        void pathRuleMatchesThePathItself(String url) {
            assertThat(matches("youtube.com/shorts", url)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://youtube.com/shorts/example",
                "https://youtube.com/shorts/example/deeper",
                "https://youtube.com/shorts/example/"
        })
        void pathRuleMatchesChildPaths(String url) {
            assertThat(matches("youtube.com/shorts", url)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://youtube.com/shorts?feature=share",
                "https://youtube.com/shorts/?feature=share",
                "https://youtube.com/shorts/example?feature=share&t=10",
                "https://youtube.com/shorts#comments"
        })
        void queryStringsAndFragmentsDoNotAffectPathMatching(String url) {
            assertThat(matches("youtube.com/shorts", url)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://youtube.com/",
                "https://youtube.com/watch",
                "https://youtube.com/shortsfoo",
                "https://youtube.com/short",
                "https://youtube.com/feed/shorts",
                "https://youtube.com/watch?next=/shorts",
                "https://youtube.com/watch?path=youtube.com/shorts"
        })
        void pathRuleDoesNotMatchOtherPathsOrPartialSegments(String url) {
            assertThat(matches("youtube.com/shorts", url)).isFalse();
        }

        @Test
        void pathRuleDoesNotMatchTheSamePathOnAnotherHost() {
            assertThat(matches("youtube.com/shorts", "https://example.com/shorts")).isFalse();
            assertThat(matches("youtube.com/shorts", "https://notyoutube.com/shorts")).isFalse();
        }

        @Test
        void multiSegmentPathRulesMatchOnSegmentBoundaries() {
            assertThat(matches("reddit.com/r/all", "https://reddit.com/r/all")).isTrue();
            assertThat(matches("reddit.com/r/all", "https://reddit.com/r/all/top")).isTrue();
            assertThat(matches("reddit.com/r/all", "https://reddit.com/r/allthethings")).isFalse();
            assertThat(matches("reddit.com/r/all", "https://reddit.com/r")).isFalse();
        }

        @Test
        void pathMatchingIsCaseInsensitive() {
            assertThat(matches("youtube.com/shorts", "https://youtube.com/Shorts/ABC")).isTrue();
            assertThat(matches("YouTube.com/Shorts", "https://youtube.com/shorts")).isTrue();
        }

        @Test
        void percentEncodedAndDotSegmentSpellingsDoNotEvadeARule() {
            assertThat(matches("youtube.com/shorts", "https://youtube.com/%73horts")).isTrue();
            assertThat(matches("youtube.com/shorts", "https://youtube.com//shorts")).isTrue();
            assertThat(matches("youtube.com/shorts", "https://youtube.com/watch/../shorts")).isTrue();
            assertThat(matches("youtube.com/shorts", "https://youtube.com/./shorts/x")).isTrue();
        }
    }

    @Nested
    class UrlParsing {

        @ParameterizedTest
        @ValueSource(strings = {
                "chrome://settings",
                "chrome-extension://abcdef/blocked.html",
                "file:///etc/hosts",
                "about:blank",
                "youtube.com",
                "not a url",
                ""
        })
        void nonWebUrlsNeverMatch(String url) {
            assertThat(matches("youtube.com", url)).isFalse();
        }

        @Test
        void nullUrlIsEmpty() {
            assertThat(TargetUrl.parse(null)).isEmpty();
        }

        @Test
        void parsesHostAndPathIntoNormalizedForm() {
            TargetUrl url = TargetUrl.parse("HTTPS://WWW.YouTube.com./Shorts/Abc/?x=1#y").orElseThrow();

            assertThat(url.host()).isEqualTo("www.youtube.com");
            assertThat(url.path()).isEqualTo("/shorts/abc");
        }

        @Test
        void siteRootHasEmptyPath() {
            assertThat(TargetUrl.parse("https://youtube.com/").orElseThrow().path()).isEmpty();
            assertThat(TargetUrl.parse("https://youtube.com").orElseThrow().path()).isEmpty();
        }
    }

    @Nested
    class Specificity {

        @Test
        void morePathSegmentsIsMoreSpecific() {
            assertThat(RuleNormalizer.normalize("example.com/docs/api"))
                    .isGreaterThan(RuleNormalizer.normalize("example.com/docs"));
            assertThat(RuleNormalizer.normalize("example.com/docs"))
                    .isGreaterThan(RuleNormalizer.normalize("example.com"));
        }

        @Test
        void moreHostLabelsIsMoreSpecific() {
            assertThat(RuleNormalizer.normalize("m.youtube.com"))
                    .isGreaterThan(RuleNormalizer.normalize("youtube.com"));
        }

        @Test
        void hostSpecificityOutranksPathSpecificity() {
            assertThat(RuleNormalizer.normalize("m.youtube.com"))
                    .isGreaterThan(RuleNormalizer.normalize("youtube.com/shorts"));
        }

        @Test
        void identicalRulesAreEquallySpecific() {
            assertThat(RuleNormalizer.normalize("example.com/docs"))
                    .isEqualByComparingTo(RuleNormalizer.normalize("EXAMPLE.com/docs/"));
        }
    }
}
