package org.urllib;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.urllib.Query.KeyValue;

public class UrlTest {

  @Test public void emptyPathIsAlwaysForwardSlash() {
    Url expected = Url.http("host").path("/").create();
    assertEquals(expected, Url.http("host").create());
    assertEquals(expected, Url.http("host").path("").create());
    assertEquals(expected, Url.http("host").path("\\").create());
  }

  @Test public void scheme() {
    Url url = Url.http("host").create();
    assertEquals(Scheme.HTTP, url.scheme());

    url = Url.https("host").create();
    assertEquals(Scheme.HTTPS, url.scheme());
  }

  @Test public void host() throws Exception {
    Url url = Url.http("host").create();
    assertEquals("host", url.host().toString());

    url = Url.http("10.10.0.1:9000").create();
    assertEquals("10.10.0.1", url.host().toString());

    url = Url.http("2001:0db8:0000:0000:0000:8a2e:0370:7334").create();
    assertEquals("2001:db8::8a2e:370:7334", url.host().toString());

    url = Url.http("[2001:db8::8a2e:370:7334]").create();
    assertEquals("2001:db8::8a2e:370:7334", url.host().toString());

    url = Url.http("[::A]:9000").create();
    assertEquals("::a", url.host().toString());
    assertEquals(9000, url.port());
  }

  @Test public void host_dontAllowInvalidIpv4() {
    assertInvalidHost("10.10.0", "Invalid hostname");
    assertInvalidHost("1.1.1", "Invalid hostname");
    assertInvalidHost("0xa.0xb.0xc.0xd", "Invalid hostname");
    assertInvalidHost("3294823", "Invalid hostname");
    assertInvalidHost("1.1.1.1.1", "Invalid hostname");
    assertInvalidHost("-1:-1:-1:-1", "Invalid hostname");
  }

  @Test public void host_dontAllowInvalidIpv6() {
    assertInvalidHost(":::", "Invalid hostname");
    assertInvalidHost("1:2:3:4:5:6:7:8:9", "Invalid hostname");
    assertInvalidHost("a::z:80", "Invalid hostname");
  }

  @Test public void host_dontAllowInvalidDns() {
    assertInvalidHost("host .com", "Invalid hostname");
    assertInvalidHost("host_name.com", "Invalid hostname");
  }

  @Test public void host_removesUserInfo() {
    Url url = Url.http("user:password@host.com").create();
    assertEquals("host.com", url.host().toString());

    url = Url.http("user@domain.com:password@host.com").create();
    assertEquals("host.com", url.host().toString());
  }

  @Test public void host_convertCharactersToLowerCase() {
    assertEquals("abcd", Url.http("ABCD").create().host().toString());
    assertEquals("σ", Url.http("Σ").create().host().toString());
  }

  @Test public void host_idnEncodedAsPunycode() {
    Url url = Url.http("bücher").create();
    assertEquals("xn--bcher-kva", url.host().name());
    assertEquals("bücher", url.host().toString());
  }

  @Test public void host_builderTakesPunycodeOrUnicode() {
    Url unicode = Url.http("bücher").create();
    Url punycode = Url.http("xn--bcher-kva").create();
    assertEquals(unicode, punycode);
    assertEquals(unicode.hashCode(), punycode.hashCode());
  }

  @Test public void port() {
    Url url = Url.http("host").create();
    assertEquals(80, url.port());

    url = Url.http("host:443").create();
    assertEquals(443, url.port());

    url = Url.http("host").port(8080).create();
    assertEquals(8080, url.port());

    url = Url.https("host").create();
    assertEquals(443, url.port());

    url = Url.https("host:80").create();
    assertEquals(80, url.port());
  }

  @Test public void fragment() {
    Url url = Url.http("host")
        .fragment("\uD83D\uDC36")
        .create();
    assertEquals("\uD83D\uDC36", url.fragment().toString());
  }

  @Test public void uriInteropAllCodepoints() {
    for (char point = 0; point < 0x100; point++) {
      String input = "" + point;
      Url url = Url.http("host.com")
          .path("/" + input)
          .query(input, input)
          .fragment(input)
          .create();

      assertEquals(url.toString(), url.uri().toString());
    }
  }

  @Test public void uriInteropSpaceAndPlus() {
    Url url = Url.parse("http://site.com/c++?q=%2B+-");
    URI uri = url.uri();
    assertEquals("/c++", uri.getPath());
    assertEquals("q=+ -", uri.getQuery());
  }

  @Test public void uriInteropUnicode() {
    Url url = Url.parse("http://❄.com/❄?q=❄#❄");
    URI uri = url.uri();
    assertEquals("xn--tdi.com", uri.getHost());
    assertEquals("/❄", uri.getPath());
    assertEquals("q=❄", uri.getQuery());
    assertEquals("❄", uri.getFragment());
  }

  @Test public void uriInteropHash() {
    Url url = Url.http("host.com")
        .path("/c#")
        .query("q", "#!")
        .fragment("#fragment#")
        .create();
    URI uri = url.uri();
    assertEquals("/c#", uri.getPath());
    assertEquals("q=#!", uri.getQuery());
    assertEquals("#fragment#", uri.getFragment());
  }

  @Test public void uriInteropIpv6() {
    Url url = Url.http("[ff::00]")
        .create();
    URI uri = url.uri();
    assertEquals("[ff::]", uri.getHost());
  }

  @Test public void allowPortWithHost() {
    assertEquals(8080, Url.http("localhost:8080").create().port());
    assertEquals(80, Url.https("localhost:80").create().port());
  }

  @Test public void trimWhitespaceBeforeParsing() {
    assertEquals(Url.http("example.com").create(), Url.parse("  http://\nexample.\n  com  "));
  }

  @Test public void parseRequiresSchemeAndHost() {
    String msg = "must have a scheme and host";
    assertInvalidParse("host.com", msg);
    assertInvalidParse("//host.com", msg);
    assertInvalidParse("../path", msg);
    assertInvalidParse("path/info.pdf", msg);
    assertInvalidParse("#fragment", msg);
  }

  @Test public void parseSchemeMustBeHttpOrHttps() {
    String msg = "http or https";
    assertInvalidParse("mysql://host.com", msg);
    assertInvalidParse("ws://host.com", msg);
    assertInvalidParse("jdbc://host.com", msg);
    assertInvalidParse("ldap://host.com", msg);
  }

  @Test public void parseSchemeIsCaseInsensitive() {
    assertEquals(Scheme.HTTP, Url.parse("HTTP://host.com").scheme());
    assertEquals(Scheme.HTTPS, Url.parse("HtTPs://host.com").scheme());
  }

  @Test public void parsePrefersPortFromInputString() {
    assertEquals(443, Url.parse("https://host.com").port());
    assertEquals(80, Url.parse("http://host.com").port());

    assertEquals(9000, Url.parse("http://host.com:9000").port());
    assertEquals(443, Url.parse("http://host.com:443").port());
    assertEquals(80, Url.parse("https://host.com:80").port());

    assertEquals(8080, Url.parse("http://[a::443]:8080").port());
    assertEquals(8080, Url.parse("http://1.1.1.1:8080").port());
  }

  @Test public void parseValidatesPort() {
    String msg = "Invalid port";
    assertInvalidParse("http://host.com:-1", msg);
    assertInvalidParse("http://host.com:0x01", msg);
    assertInvalidParse("http://host.com:10000000", msg);
  }

  @Test public void parseValidatesHost() {
    assertInvalidParse("http:////", "missing host");
    assertInvalidParse("http://?", "missing host");
    assertInvalidParse("http://#", "missing host");
    assertInvalidParse("http://host\u2000.com", "Invalid host");
    assertInvalidParse("http://!@[", "Invalid host");
    assertInvalidParse("http://[a:1:b]", "Invalid host");
    assertInvalidParse("http://[1:2:3:4:5:6:7:8:9]", "Invalid host");
    assertInvalidParse("http://192.168", "Invalid host");
    assertInvalidParse("http://1.1.1.1.1", "Invalid host");

    // Don't allow leading zeroes in ipv4 since it's unclear whether the encoding
    // is decimal or octal.
    assertInvalidParse("http://01.01.01.01", "Invalid host");
  }

  @Test public void parseConvertsDnsHostToLowerCase() {
    assertEquals("host.com", Url.parse("http://HOST.com").host().name());
    assertEquals("host.com", Url.parse("http://HoSt.COM").host().name());
  }

  @Test public void parseCompressesIpv6() {
    assertEquals("[a::1]", Url.parse("http://[a:0:0:0:0:0:0:1]").host().name());
    assertEquals("[ff::]", Url.parse("http://[FF::0:0]").host().name());
    assertEquals("[::a:b:0:0:0]", Url.parse("http://[0:0:0:a:b:00:000:0]").host().name());
  }

  @Test public void parseAlwaysReturnsUrlWithPath() {
    Path expected = Path.empty();
    assertEquals(expected, Url.parse("http://host.com").path());
    assertEquals(expected, Url.parse("http://host.com/").path());
    assertEquals(expected, Url.parse("http://host.com?query").path());
    assertEquals(expected, Url.parse("http://host.com#fragment").path());
  }

  @Test public void parseRemovesDotSegmentsInPath() {
    assertEquals(Path.of("a/b/"), Url.parse("http://host.com/a/b/").path());
    assertEquals(Path.of("a/b/"), Url.parse("http://host.com/a/b/.").path());
    assertEquals(Path.of("a/b/"), Url.parse("http://host.com/a/b/c/..").path());
    assertEquals(Path.of("a/b/"), Url.parse("http://host.com/a/b/c/../.").path());
    assertEquals(Path.of("a/"), Url.parse("http://host.com/a/b/c/../..").path());
    assertEquals(Path.of("a/b/file.html"), Url.parse("http://host.com/a/b/c/../file.html").path());

    assertEquals(Path.of("a/b/"), Url.parse("http://host.com/a/b/%2e").path());
    assertEquals(Path.of("a/b/"), Url.parse("http://host.com/a/b/c/%2E%2e").path());
  }

  @Test public void parseRemovesEmptyQueryValues() {
    Query expected = Query.of(Arrays.asList(KeyValue.create("k", "")));
    assertEquals(expected, Url.parse("http://host.com?k=").query());
    assertEquals(expected, Url.parse("http://host.com?k").query());
  }

  @Test public void parseRetainsDuplicateKeysInQuery() {
    Query expected = Query.of(
        Arrays.asList(KeyValue.create("k", "a"), KeyValue.create("k", "b")));
    assertEquals(expected, Url.parse("http://host.com?k=a&k=b").query());
  }

  @Test public void parseRemovesPercentEncoding() {
    Url decoded = Url.parse("http://host.com/docs/résumé.html?q=\uD83D\uDC3C#\uD83D\uDE03");
    Url encoded = Url.parse("http://host.com/docs/r%C3%A9sum%C3%A9.html?q=%F0%9F%90%BC#%F0%9F%98%83");
    assertEquals(decoded, encoded);
    assertEquals(decoded, encoded);
  }

  @Test public void parseHandlesSlashesInBothDirections() {
    assertEquals(Url.parse("http://host.com/a/b/"), Url.parse("http:\\\\host.com\\a\\b\\"));
  }

  private void assertInvalidParse(String url, String msg) {
    try {
      Url.parse(url);
      fail("Expected IllegalArgumentException for: " + url);
    } catch (IllegalArgumentException expected) {
      if (!expected.getMessage().contains(msg)) {
        throw expected;
      }
    }
  }

  private void assertInvalidHost(String host, String msg) {
    try {
      Url.http(host);
      fail("Expected IllegalArgumentException for: " + host);
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage(), containsString(msg));
    }
  }
}