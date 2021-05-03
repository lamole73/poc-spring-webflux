package lo.poc.webflux.adapter.rest.client.domain.mapper;

import lo.poc.webflux.adapter.rest.client.domain.Person;
import lo.poc.webflux.adapter.rest.controller.domain.PersonVo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PersonMapper {
    PersonMapper INSTANCE = Mappers.getMapper(PersonMapper.class);

    PersonVo map(Person client);

}
