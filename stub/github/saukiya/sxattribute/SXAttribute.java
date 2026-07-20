package github.saukiya.sxattribute;

import github.saukiya.sxattribute.data.attribute.SXAttributeData;
import java.util.UUID;

public class SXAttribute {

    private static final SXAttribute api = new SXAttribute();

    public static SXAttribute getApi() {
        return api;
    }

    public void setEntityAPIData(Class<?> clazz, UUID uuid, SXAttributeData data) {
    }

    public void removeEntityAPIData(Class<?> clazz, UUID uuid) {
    }
}
