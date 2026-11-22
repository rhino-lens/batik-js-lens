package example.app;

import java.util.ArrayList;
import java.util.List;

/** A service scripts call to store things. */
public class AuditService {

    public final List<Object> records = new ArrayList<>();

    public void record(Object value) {
        records.add(value);
    }
}
