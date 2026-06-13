package fpt.swp391.GlucoTrackAlert.converter;

import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MetricTypeConverter implements AttributeConverter<MetricType, String> {

    @Override
    public String convertToDatabaseColumn(MetricType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public MetricType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MetricType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
