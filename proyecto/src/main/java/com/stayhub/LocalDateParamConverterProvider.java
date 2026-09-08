package com.stayhub;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDate;

/**
 * RESTEASY (el JAX-RS de WildFly) solo puede convertir automáticamente un
 * @QueryParam/@PathParam a un tipo si ese tipo tiene un constructor de un
 * solo String, o un valueOf(String)/fromString(String) estático. LocalDate
 * no tiene ninguno de los tres (tiene parse(CharSequence), que no cuenta
 * para la especificación JAX-RS) — sin este Provider, cualquier endpoint
 * que reciba un LocalDate por query param/path param rompe el despliegue
 * de TODO el WAR con RESTEASY003875.
 *
 * Al ser @Provider, RESTEASY lo detecta solo (mismo mecanismo que ya usan
 * los *ExceptionMapper de cada componente) — no hace falta registrarlo en
 * RestApplication ni tocar ningún endpoint existente.
 */
@Provider
public class LocalDateParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != LocalDate.class) {
            return null;
        }
        return (ParamConverter<T>) new ParamConverter<LocalDate>() {
            @Override
            public LocalDate fromString(String value) {
                return value == null || value.isBlank() ? null : LocalDate.parse(value);
            }

            @Override
            public String toString(LocalDate value) {
                return value == null ? null : value.toString();
            }
        };
    }
}
