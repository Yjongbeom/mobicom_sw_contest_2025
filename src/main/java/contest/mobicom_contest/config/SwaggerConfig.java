package contest.mobicom_contest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

//    // not use jwt
//    @Bean
//    public OpenAPI SwaggerConfig{
//        return new OpenAPI().components(new Components()).info(apiInfo())
//    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList("JWT"))
                .components(new Components()
                        .addSecuritySchemes("JWT", new SecurityScheme()
                                .name("JWT")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                        )
                );
    }

    private Info apiInfo() {
        return new Info()
                .title("API Test") // API의 제목
                .description("Let's practice Swagger UI")
                .version("1.0.0"); // API의 버전
    }
}
