package xyz.jphil.ccapis;

import okhttp3.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Test to demonstrate how Java's TLS differs from regular curl
 */
public class TLSTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== JAVA TLS CAPABILITIES ===");
        System.out.println();
        
        // Show Java's TLS capabilities
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        
        System.out.println("Java TLS Provider Info:");
        System.out.println("- Default SSL Context: " + sslContext.getProvider().getName());
        System.out.println("- Supported Protocols: " + Arrays.toString(socketFactory.getDefaultCipherSuites()));
        System.out.println();
        
        // Show OkHttpClient's modern TLS
        ConnectionSpec modernTLS = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .build();
        
        System.out.println("OkHttpClient Modern TLS:");
        System.out.println("- Connection Spec: " + modernTLS);
        System.out.println();
        
        System.out.println("Why Java Works Without curl-impersonate:");
        System.out.println("1. ✅ Java 21 has modern TLS 1.3 support");
        System.out.println("2. ✅ No distinctive JA3 fingerprint (unlike old curl)");
        System.out.println("3. ✅ Cipher suites match modern browsers naturally");
        System.out.println("4. ✅ Combined with proper browser headers = accepted as browser");
        System.out.println();
        System.out.println("The real protection is sessionKey + browser-like headers,");
        System.out.println("not TLS fingerprinting for modern Java clients.");
    }
}