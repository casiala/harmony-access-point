package eu.domibus.web.rest;

import com.google.common.collect.ImmutableMap;
import eu.domibus.api.property.DomibusProperty;
import eu.domibus.api.property.DomibusPropertyException;
import eu.domibus.api.property.DomibusPropertyMetadata;
import eu.domibus.api.validators.SkipWhiteListed;
import eu.domibus.core.converter.DomibusCoreMapper;
import eu.domibus.core.property.DomibusPropertiesFilter;
import eu.domibus.core.property.DomibusPropertyMetadataMapper;
import eu.domibus.core.property.DomibusPropertyResourceHelper;
import eu.domibus.logging.DomibusLoggerFactory;
import eu.domibus.web.rest.error.ErrorHandlerService;
import eu.domibus.web.rest.ro.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ion Perpegel
 * @since 4.1.1
 * <p>
 * Resource responsible for getting the domibus properties that can be changed at runtime, getting and setting their values through REST Api
 */
@RestController
@RequestMapping(value = "/rest/configuration/properties")
@Validated
public class DomibusPropertyResource extends BaseResource {
    private static final Logger LOG = DomibusLoggerFactory.getLogger(DomibusPropertyResource.class);

    private final DomibusPropertyResourceHelper domibusPropertyResourceHelper;

    private final DomibusPropertyMetadataMapper domibusPropertyMetadataMapper;

    private final ErrorHandlerService errorHandlerService;

    public DomibusPropertyResource(DomibusPropertyResourceHelper domibusPropertyResourceHelper,
                                   DomibusPropertyMetadataMapper domibusPropertyMetadataMapper,
                                   ErrorHandlerService errorHandlerService) {
        this.domibusPropertyResourceHelper = domibusPropertyResourceHelper;
        this.domibusPropertyMetadataMapper = domibusPropertyMetadataMapper;
        this.errorHandlerService = errorHandlerService;
    }

    @ExceptionHandler({DomibusPropertyException.class})
    public ResponseEntity<ErrorRO> handleDomibusPropertyException(DomibusPropertyException ex) {
        Throwable rootCause = ExceptionUtils.getRootCause(ex);
        String message = rootCause == null ? ex.getMessage() : rootCause.getMessage();
        return errorHandlerService.createResponse(message, HttpStatus.BAD_REQUEST);
    }

    @GetMapping
    public PropertyResponseRO getProperties(@Valid PropertyFilterRequestRO request) {
        PropertyResponseRO response = new PropertyResponseRO();

        DomibusPropertiesFilter filter = domibusPropertyMetadataMapper.domibusPropertyFilterRequestTOdomibusPropertiesFilter(request);
        List<DomibusProperty> items = domibusPropertyResourceHelper.getAllProperties(filter);

        response.setCount(items.size());
        items = items.stream()
                .skip((long) request.getPage() * request.getPageSize())
                .limit(request.getPageSize())
                .collect(Collectors.toList());

        List<DomibusPropertyRO> convertedItems = domibusPropertyMetadataMapper.domibusPropertyListToDomibusPropertyROList(items);

        response.setItems(convertedItems);

        return response;
    }

    /**
     * Sets the specified value for the specified property name
     * We skip the default blacklist validator because some properties have values that ae normally in the black-list
     *
     * @param propertyName  the name of the property
     * @param isDomain      tells if it is set in a domain context
     * @param propertyValue the value of the property
     */
    @PutMapping(path = "/{propertyName:.+}")
    @SkipWhiteListed
    public void setProperty(@PathVariable String propertyName,
                            @RequestParam(required = false, defaultValue = "true") boolean isDomain,
                            @Valid @RequestBody(required = false) String propertyValue) {

        // sanitize empty body sent by various clients
        propertyValue = StringUtils.trimToEmpty(propertyValue);

        domibusPropertyResourceHelper.setPropertyValue(propertyName, isDomain, propertyValue);
    }

    /**
     * Exports to CSV
     */
    @GetMapping(path = "/csv")
    public ResponseEntity<String> getCsv(@Valid PropertyFilterRequestRO request) {
        DomibusPropertiesFilter filter = domibusPropertyMetadataMapper.domibusPropertyFilterRequestTOdomibusPropertiesFilter(request);
        List<DomibusProperty> items = domibusPropertyResourceHelper.getAllProperties(filter);

        getCsvService().validateMaxRows(items.size());

        List<DomibusPropertyRO> convertedItems = domibusPropertyMetadataMapper.domibusPropertyListToDomibusPropertyROList(items);

        return exportToCSV(convertedItems, DomibusPropertyRO.class,
                ImmutableMap.of("name".toUpperCase(), "Property Name",
                        "usageText".toUpperCase(), "Usage",
                        "writable".toUpperCase(), "Is Writable",
                        "encrypted".toUpperCase(), "Is Encrypted",
                        "value".toUpperCase(), "Property Value"),
                Arrays.asList("clusterAware"),
                "domibusProperties");
    }

    /**
     * Retrieves the domibus property types (along with their regular expression) as a list,
     * To be used in client validation
     *
     * @return a list of property types
     */
    @RequestMapping(value = "metadata/types", method = RequestMethod.GET)
    public List<DomibusPropertyTypeRO> getDomibusPropertyMetadataTypes() {
        LOG.debug("Getting domibus property metadata types.");

        DomibusPropertyMetadata.Type[] types = DomibusPropertyMetadata.Type.values();
        List<DomibusPropertyTypeRO> res = domibusPropertyMetadataMapper.domibusPropertyMetadataTypeListToDomibusPropertyTypeROList(Arrays.asList(types));
        return res;
    }

    /**
     * Returns the property metadata and the current value for a property
     *
     * @param propertyName the name of the property
     * @return object containing both metadata and value
     */
    @GetMapping(path = "/{propertyName:.+}")
    public DomibusPropertyRO getProperty(@Valid @PathVariable String propertyName) {
        DomibusProperty prop = domibusPropertyResourceHelper.getProperty(propertyName);
        DomibusPropertyRO convertedProp = domibusPropertyMetadataMapper.propertyApiToPropertyRO(prop);
        return convertedProp;
    }
}
