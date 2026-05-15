package edu.cqupt.devbrain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiShoppingAgentApplicationProxyTest {

    @Test
    void configureJvmSystemProxiesShouldEnableSystemProxyWhenUnset() {
        String previous = System.getProperty("java.net.useSystemProxies");
        System.clearProperty("java.net.useSystemProxies");
        try {
            AiShoppingAgentApplication.configureJvmSystemProxies();

            assertThat(System.getProperty("java.net.useSystemProxies")).isEqualTo("true");
        } finally {
            restoreUseSystemProxies(previous);
        }
    }

    @Test
    void configureJvmSystemProxiesShouldNotOverrideExplicitJvmSetting() {
        String previous = System.getProperty("java.net.useSystemProxies");
        System.setProperty("java.net.useSystemProxies", "false");
        try {
            AiShoppingAgentApplication.configureJvmSystemProxies();

            assertThat(System.getProperty("java.net.useSystemProxies")).isEqualTo("false");
        } finally {
            restoreUseSystemProxies(previous);
        }
    }

    private void restoreUseSystemProxies(String previous) {
        if (previous == null) {
            System.clearProperty("java.net.useSystemProxies");
        } else {
            System.setProperty("java.net.useSystemProxies", previous);
        }
    }
}
