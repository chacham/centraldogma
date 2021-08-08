/*
 * Copyright 2020 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.centraldogma.server.auth.shiro;

import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.MALFORMED_SESSION_ID;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.PASSWORD;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.USERNAME;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.WRONG_PASSWORD;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.WRONG_SESSION_ID;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.login;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.loginWithBasicAuth;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.logout;
import static com.linecorp.centraldogma.testing.internal.auth.TestAuthMessageUtil.usersMe;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.shiro.config.Ini;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.centraldogma.internal.api.v1.AccessToken;
import com.linecorp.centraldogma.internal.jackson.Jackson;
import com.linecorp.centraldogma.server.CentralDogmaBuilder;
import com.linecorp.centraldogma.server.auth.AuthProvider;
import com.linecorp.centraldogma.testing.junit.CentralDogmaExtension;

class ShiroLoginAndLogoutTest {

    @RegisterExtension
    final CentralDogmaExtension dogma = new CentralDogmaExtension() {
        @Override
        protected void configure(CentralDogmaBuilder builder) {
            builder.authProviderFactory(new ShiroAuthProviderFactory(unused -> {
                final Ini iniConfig = new Ini();
                iniConfig.addSection("users").put(USERNAME, PASSWORD);
                return iniConfig;
            }));
            builder.webAppEnabled(true);
        }

        @Override
        protected boolean runForEachTest() {
            return true;
        }
    };

    private WebClient client;

    @BeforeEach
    void setClient() {
        client = dogma.httpClient();
    }

    @Test
    void password() throws Exception { // grant_type=password
        loginAndLogout(login(client, USERNAME, PASSWORD));
    }

    private void loginAndLogout(AggregatedHttpResponse loginRes) throws Exception {
        assertThat(loginRes.status()).isEqualTo(HttpStatus.OK);

        // Ensure authorization works.
        final AccessToken accessToken = Jackson.ofJson().readValue(loginRes.contentUtf8(), AccessToken.class);
        final String sessionId = accessToken.accessToken();

        assertThat(usersMe(client, sessionId).status()).isEqualTo(HttpStatus.OK);

        // Log out.
        assertThat(logout(client, sessionId).status()).isEqualTo(HttpStatus.OK);
        assertThat(usersMe(client, sessionId).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void basicAuth() throws Exception {
        loginAndLogout(loginWithBasicAuth(client, USERNAME, PASSWORD));
    }

    @Test
    void incorrectLogin() {
        assertThat(login(client, USERNAME, WRONG_PASSWORD).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void incorrectLogout() {
        assertThat(logout(client, WRONG_SESSION_ID).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(logout(client, MALFORMED_SESSION_ID).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldUseBuiltinWebPages() {
        AggregatedHttpResponse resp;
        resp = client.get(AuthProvider.LOGIN_PATH).aggregate().join();
        assertThat(resp.status()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(resp.headers().get(HttpHeaderNames.LOCATION))
                .isEqualTo(AuthProvider.BUILTIN_WEB_LOGIN_PATH);

        resp = client.get(AuthProvider.LOGOUT_PATH).aggregate().join();
        assertThat(resp.status()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(resp.headers().get(HttpHeaderNames.LOCATION))
                .isEqualTo(AuthProvider.BUILTIN_WEB_LOGOUT_PATH);
    }
}
