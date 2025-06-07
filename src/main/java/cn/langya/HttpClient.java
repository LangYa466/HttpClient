package cn.langya;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author LangYa466
 * @since 4/14/2025
 */
public class HttpClient {
    private String url;
    private HttpMethod method = HttpMethod.GET;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> params = new HashMap<>();
    private final Map<String, String> cookies = new HashMap<>();
    private Proxy proxy = null;
    private int timeout = 5000; // 5秒

    public HttpClient proxy(String host, int port, ProxyType type) {
        Proxy.Type proxyType = type == ProxyType.SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        this.proxy = new Proxy(proxyType, new InetSocketAddress(host, port));
        return this;
    }

    public HttpClient proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public HttpClient url(String url) {
        this.url = url;
        return this;
    }

    public HttpClient method(HttpMethod method) {
        this.method = method;
        return this;
    }

    public HttpClient header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public HttpClient header(String key, UserAgent userAgent) {
        headers.put(key, userAgent.getUa());
        return this;
    }

    public HttpClient headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }

    public HttpClient param(String key, String value) {
        params.put(key, value);
        return this;
    }

    public HttpClient params(Map<String, String> params) {
        this.params.putAll(params);
        return this;
    }

    public HttpClient cookie(String key, String value) {
        cookies.put(key, value);
        return this;
    }

    public HttpClient cookies(Map<String, String> cookies) {
        this.cookies.putAll(cookies);
        return this;
    }

    public HttpClient userAgent(UserAgent ua) {
        return header("User-Agent", ua.getUa());
    }

    public HttpClient timeout(int timeoutMillis) {
        this.timeout = timeoutMillis;
        return this;
    }

    public Response send() throws IOException {
        String realUrl = url;
        if (method == HttpMethod.GET && !params.isEmpty()) {
            realUrl += (url.contains("?") ? "&" : "?") + buildParams(params);
        }

        HttpURLConnection conn = proxy == null
                ? (HttpURLConnection) new URL(realUrl).openConnection()
                : (HttpURLConnection) new URL(realUrl).openConnection(proxy);
        conn.setRequestMethod(method.name());
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        // 设置 Headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        // 设置 Cookies
        if (!cookies.isEmpty()) {
            conn.setRequestProperty("Cookie", buildCookies(cookies));
        }

        // POST 参数
        if (method == HttpMethod.POST && !params.isEmpty()) {
            conn.setDoOutput(true);
            String postData = buildParams(params);
            OutputStream out = conn.getOutputStream();
            out.write(postData.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        }

        // 读取返回
        int code = conn.getResponseCode();
        InputStream is = code < HttpURLConnection.HTTP_BAD_REQUEST ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder resp = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            resp.append(line).append("\n");
        }
        reader.close();

        Map<String, String> respCookies = getCookies(conn);

        return new Response(code, resp.toString().trim(), respCookies);
    }

    private static Map<String, String> getCookies(HttpURLConnection conn) {
        Map<String, List<String>> headerFields = conn.getHeaderFields();
        List<String> setCookieHeaders = headerFields.get("Set-Cookie");
        Map<String, String> respCookies = new HashMap<>();
        if (setCookieHeaders != null) {
            for (String cookie : setCookieHeaders) {
                String[] parts = cookie.split(";", 2);
                String[] kv = parts[0].split("=", 2);
                if (kv.length == 2) {
                    respCookies.put(kv[0], kv[1]);
                }
            }
        }
        return respCookies;
    }

    private String buildParams(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    private String buildCookies(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}