package lo.poc.webflux.adapter.rest.error;

public class AdapterException extends RuntimeException {
    public AdapterException(String s) {
        super(s);
    }
}
