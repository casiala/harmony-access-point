package eu.domibus.api.routing;

import eu.domibus.api.validators.CustomWhiteListed;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;

/**
 * @author Tiago Miguel
 * @since 3.3
 */
public class RoutingCriteria implements Serializable {

    private String entityId;

    private String name;

    @CustomWhiteListed(permitted = ":/?&-+%")
    private String expression;

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("entityId", entityId)
                .append("name", name)
                .append("expression", expression)
                .toString();
    }
}
