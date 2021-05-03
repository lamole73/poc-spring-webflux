package lo.poc.webflux.adapter.rest.error;

public class PersonNotFoundException extends AdapterException {
    public PersonNotFoundException(String s) {
        super(s);
    }
}
