# ServicePolicy

`public interface ServicePolicy` (functional): decides which Java objects are services. Every method a script calls on a service is recorded as a [ServiceCall](ServiceCall.md). Objects registered with `Lens.registerService` are always services.

| Method | Notes |
|---|---|
| `String serviceName(Object target)` | The name to show if `target` is a service, else `null`. Exceptions are flagged and treated as "not a service". |
| `default ServicePolicy or(ServicePolicy other)` | This policy first, then `other`. |
| `static defaults()` | Annotated `org.springframework.stereotype.Service` / `…Repository` (on the class, a superclass or an interface; by name, with no Spring dependency), or a simple class name ending in `Service`, `Repository` or `Dao`. |
| `static none()` | No object (only registered ones). |
| `static annotations(String... annotationClassNames)` | Annotated classes. |
| `static classNameSuffixes(String... suffixes)` | Simple class name ends with a suffix (Spring/CGLIB `$$` proxy suffixes removed). |
| `static types(Class<?>... types)` | Instances of these types. |

The built-in policies cache their decision per class, so a check costs one lookup. The default name is the simple class name with a lowercase first letter (`OrderRepository` → `orderRepository`).

<!-- snippet: register-service -->
```java
Lens.registerService(auditService, "auditService");   // always a service, whatever its class
```
