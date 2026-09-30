package system;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SystemClockConverter implements AttributeConverter<SystemClock, Long> {

    @Override
    public Long convertToDatabaseColumn(SystemClock attribute) {
        return (attribute == null) ? null : attribute.toEpochMilli();
    }

    @Override
    public SystemClock convertToEntityAttribute(Long dbData) {
        return (dbData == null) ? null : SystemClock.ofEpochMilli(dbData);
    }
}