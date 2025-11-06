package xyz.jphil.ccapis.util;

import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;
import xyz.jphil.ccapis.config.CCAPIConfig;

public class TestParsing {
    public static void main(String[] args) {
        // Test the parsing of the test settings file
        CCAPIConfig config = CurlCommandCredentialExtractor.extractCredentialsFromTestSettings();
        
        System.out.println("Found " + config.accounts().size() + " account(s)");
        
        for (CCAPIConfig.Account account : config.accounts()) {
            System.out.println("\nAccount Details:");
            System.out.println("  Org ID: " + account.orgId());
            System.out.println("  Session Key: " + account.sessionKey());
            System.out.println("  User Agent: " + account.userAgent());
            System.out.println("  Additional Headers Count: " + account.additionalHeaders().size());
            System.out.println("  Cookies Count: " + account.cookies().size());
            
            System.out.println("\nSample Cookies:");
            int count = 0;
            for (String cookieName : account.cookies().keySet()) {
                if (count++ > 5) { // Show first 5 cookies as sample
                    System.out.println("  ... (and " + (account.cookies().size() - 5) + " more)");
                    break;
                }
                System.out.println("  " + cookieName + " = " + account.cookies().get(cookieName));
            }
            
            System.out.println("\nSample Headers:");
            count = 0;
            for (String headerName : account.additionalHeaders().keySet()) {
                if (count++ > 5) { // Show first 5 headers as sample
                    System.out.println("  ... (and " + (account.additionalHeaders().size() - 5) + " more)");
                    break;
                }
                System.out.println("  " + headerName + " = " + account.additionalHeaders().get(headerName));
            }
        }
    }
}