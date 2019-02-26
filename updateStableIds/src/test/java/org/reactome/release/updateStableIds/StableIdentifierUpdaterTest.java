package org.reactome.release.updateStableIds;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.matchers.Null;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.release.common.database.InstanceEditUtils;
import org.powermock.modules.junit4.PowerMockRunner;

import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({StableIdentifierUpdater.class, InstanceEditUtils.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
public class StableIdentifierUpdaterTest {

    private MySQLAdaptor mockAdaptor = PowerMockito.mock(MySQLAdaptor.class);
    private MySQLAdaptor mockAdaptor2 = PowerMockito.mock(MySQLAdaptor.class);
    private MySQLAdaptor mockAdaptor3 = PowerMockito.mock(MySQLAdaptor.class);

//    @Mock
//    UpdateStableIds stableUpdater = new UpdateStableIds();

    @Mock
    private GKInstance mockInstanceEdit;

    @Mock
    private GKInstance mockInstance;
    @Mock
    private GKInstance mockInstance2;
    @Mock
    private GKInstance mockInstance3;
    @Mock
    private GKInstance mockInstanceNull = null;

    @Mock
    private SchemaClass mockSchemaClass;

    private List<GKInstance> sliceList;
    private List<GKInstance> sliceList2;
    private List<GKInstance> sliceListNull;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void stableIdUpdaterTest() throws Exception {

        PowerMockito.mockStatic(InstanceEditUtils.class);
//        Mockito.when(InstanceEditUtils.createInstanceEdit(mockAdaptor, 12345L, "CreatorName")).thenReturn(mockInstanceEdit);

        sliceList = Arrays.asList(mockInstance, mockInstance, mockInstance);
        sliceList2 = Arrays.asList(mockInstance, mockInstance);

        Mockito.when(mockAdaptor.fetchInstancesByClass("Event")).thenReturn(sliceList);
        Mockito.when(mockAdaptor.fetchInstancesByClass("PhysicalEntity")).thenReturn(sliceList);

        Mockito.when(mockAdaptor2.fetchInstance(mockInstance.getDBID())).thenReturn(mockInstance2);
        Mockito.when(mockAdaptor3.fetchInstance(mockInstance.getDBID())).thenReturn(mockInstance3);

        Mockito.when(mockInstance.getAttributeValuesList("modified")).thenReturn(sliceList);
        Mockito.when(mockInstance2.getAttributeValuesList("modified")).thenReturn(sliceList2);

        Mockito.when(mockInstance.getAttributeValue("stableIdentifier")).thenReturn(mockInstance);
        Mockito.when(mockInstance3.getAttributeValue("stableIdentifier")).thenReturn(mockInstance);

        Mockito.when(mockInstance.getAttributeValue("identifierVersion")).thenReturn("1");

        Mockito.when(mockInstance.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockInstance.getSchemClass().isa("Event")).thenReturn(true);

        Mockito.when(mockInstance.getAttributeValuesList("reviewed")).thenReturn(sliceList);
        Mockito.when(mockInstance2.getAttributeValuesList("reviewed")).thenReturn(sliceList2);

        StableIdentifierUpdater.updateStableIdentifiers(mockAdaptor, mockAdaptor2, mockAdaptor3, 12345L);
    }

    @Test
    public void stableIdUpdaterModifiedListTest() throws Exception {
        PowerMockito.mockStatic(InstanceEditUtils.class);

        sliceList = Arrays.asList(mockInstance);
        sliceList2 = Arrays.asList(mockInstance, mockInstance);

        Mockito.when(mockAdaptor.fetchInstancesByClass("Event")).thenReturn(sliceList);
        Mockito.when(mockAdaptor.fetchInstancesByClass("PhysicalEntity")).thenReturn(sliceList);

        Mockito.when(mockAdaptor2.fetchInstance(mockInstance.getDBID())).thenReturn(mockInstance2);
        Mockito.when(mockAdaptor3.fetchInstance(mockInstance.getDBID())).thenReturn(mockInstance3);

        Mockito.when(mockInstance.getAttributeValuesList("modified")).thenReturn(sliceList);
        Mockito.when(mockInstance2.getAttributeValuesList("modified")).thenReturn(sliceList2);

        Mockito.when(mockInstance.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockInstance.getSchemClass().isa("Event")).thenReturn(false);

        StableIdentifierUpdater.updateStableIdentifiers(mockAdaptor, mockAdaptor2, mockAdaptor3, 12345L);
    }
}