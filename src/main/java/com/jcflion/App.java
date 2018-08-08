package com.jcflion;

import com.jcflion.gray.GrayConfigManager;

/**
 * @author kanner
 */
public class App {

    public static void main(String[] args) throws InterruptedException {
        new InitConfig("app1", "http://127.0.0.1:8080").init();
        while (true) {
            Thread.sleep(5000L);
            System.out.println("app1-test-file.cflion-key1=" + ConfigManager.getConfig("app1-test-file.cflion-key1"));
            System.out.println("app2-test-file.cflion-key2=" + ConfigManager.getConfig("app2-test-file.cflion-key2"));
            System.out.println("app3-test-file.cflion-key3=" + ConfigManager.getConfig("app3-test-file.cflion-key3"));
            System.out.println("account1 is allowd=" + GrayConfigManager.isAllowed("app3-test-file.cflion-key3", "account1"));
        }
    }
}
