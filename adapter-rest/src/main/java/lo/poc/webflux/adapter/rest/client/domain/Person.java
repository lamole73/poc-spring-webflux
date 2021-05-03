package lo.poc.webflux.adapter.rest.client.domain;

import lombok.*;

@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class Person {
    private Long id;
    private String name;
    private String email;
    private String phone;
}
