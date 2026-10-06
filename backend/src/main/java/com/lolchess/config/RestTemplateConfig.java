package com.lolchess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * [역할] 외부 HTTP API(라이엇 Data Dragon) 호출에 사용할 RestTemplate 빈 설정.
 *
 * [Data Flow]
 *   RiotDataDragonService --> RestTemplate --> https://ddragon.leagueoflegends.com
 *   타임아웃을 지정해 외부 서버 응답이 없을 때 서버 기동이 무한정 멈추지 않도록 한다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(requestFactory);
    }
}
