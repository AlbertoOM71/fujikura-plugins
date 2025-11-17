package org.apache.ofbiz.planning.machinery;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.*;

public class MachineryServices {

    public static final String module = MachineryServices.class.getName();

    public static Map<String, Object> searchMachinery(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        List<Map<String, String>> results = new ArrayList<>();
        
        String facilityName = (String) context.get("facilityName");
        String machineId = (String) context.get("machineId");
        String fixedAssetId = (String) context.get("fixedAssetId");
        String name = (String) context.get("name");
        String machineTypeId = (String) context.get("machineTypeId");

        try {
        	Debug.logInfo("=== INICIANDO BÚSQUEDA DE MAQUINARIA ===", module);
            Debug.logInfo("Parámetros recibidos - facilityName: " + facilityName + ", machineId: " + machineId + 
                         ", fixedAssetId: " + fixedAssetId + ", name: " + name + ", machineTypeId: " + machineTypeId, module);
            
            // 1. Filtrar Facilities por facilityName si aplica
            Set<String> facilityIds = new HashSet<>();
            if (facilityName != null && !facilityName.isEmpty()) {
                List<GenericValue> matchingFacilities = EntityQuery.use(delegator)
                        .from("Facility")
                        .where(EntityCondition.makeCondition("facilityName", EntityOperator.LIKE, "%" + facilityName + "%"))
                        .queryList();
                
                Debug.logInfo("Facilities encontradas con filtro: " + matchingFacilities.size(), module);

                if (matchingFacilities.isEmpty()) {
                    Map<String, Object> emptyResult = ServiceUtil.returnSuccess();
                    emptyResult.put("machineryList", results);
                    return emptyResult;
                }

                for (GenericValue facility : matchingFacilities) {
                    facilityIds.add(facility.getString("facilityId"));
                }
            }
            
            // 2. Construir condiciones para FixedAsset
            List<EntityCondition> fixedAssetConditions = new ArrayList<>();

            // Filtro por Asset ID
            if (fixedAssetId != null && !fixedAssetId.isEmpty()) {
                fixedAssetConditions.add(EntityCondition.makeCondition("fixedAssetId", EntityOperator.LIKE, "%" + fixedAssetId + "%"));
            }
            
            // Filtro por Name
            if (name != null && !name.isEmpty()) {
                fixedAssetConditions.add(EntityCondition.makeCondition("fixedAssetName", EntityOperator.LIKE, "%" + name + "%"));
            }
            
            // Filtro por Machine Type
            if (machineTypeId != null && !machineTypeId.isEmpty()) {
                fixedAssetConditions.add(EntityCondition.makeCondition("fixedAssetTypeId", EntityOperator.EQUALS, machineTypeId));
            }

            // Filtrar solo registros no eliminados
            fixedAssetConditions.add(EntityCondition.makeCondition("actualEndOfLife", EntityOperator.EQUALS, null));
            
            // Filtrar por facilityId si aplica
            if (!facilityIds.isEmpty()) {
                fixedAssetConditions.add(EntityCondition.makeCondition("locatedAtFacilityId", EntityOperator.IN, facilityIds));
            }
            
            EntityCondition fixedAssetCondition = null;
            if (!fixedAssetConditions.isEmpty()) {
                fixedAssetCondition = EntityCondition.makeCondition(fixedAssetConditions, EntityOperator.AND);
            }
            
            // 3. Consultar FixedAssets
            List<GenericValue> fixedAssets = EntityQuery.use(delegator)
                    .from("FixedAsset")
                    .where(fixedAssetCondition)
                    .queryList();
            
            Debug.logInfo("FixedAssets encontrados: " + fixedAssets.size(), module);

            if (fixedAssets.isEmpty()) {
                Map<String, Object> emptyResult = ServiceUtil.returnSuccess();
                emptyResult.put("machineryList", results);
                return emptyResult;
            }
            
            // 4. Pre-cargar todas las Facilities relacionadas para mejor performance
            Map<String, String> facilityNames = new HashMap<>();
            Set<String> allFacilityIds = new HashSet<>();
            for (GenericValue fixedAsset : fixedAssets) {
                String facId = fixedAsset.getString("locatedAtFacilityId");
                if (facId != null) {
                    allFacilityIds.add(facId);
                }
                
                // DEBUG: Mostrar información de cada FixedAsset
                Debug.logInfo("FixedAsset ID: " + fixedAsset.getString("fixedAssetId") + 
                             " - Name: " + fixedAsset.getString("fixedAssetName") +
                             " - Type: " + fixedAsset.getString("fixedAssetTypeId") +
                             " - FacilityId: " + facId, module);
            }
            
            if (!allFacilityIds.isEmpty()) {
                List<GenericValue> facilities = EntityQuery.use(delegator)
                        .from("Facility")
                        .where(EntityCondition.makeCondition("facilityId", EntityOperator.IN, allFacilityIds))
                        .queryList();
                for (GenericValue facility : facilities) {
                    facilityNames.put(facility.getString("facilityId"), facility.getString("facilityName"));
                    Debug.logInfo("Mapeo Facility - ID: " + facility.getString("facilityId") + " -> Name: " + facility.getString("facilityName"), module);
                }
            }
            
            // 5. Pre-cargar todos los Plant Tags (Machine IDs) para mejor performance
            Map<String, String> plantTags = new HashMap<>();
            Set<String> fixedAssetIds = new HashSet<>();
            for (GenericValue fixedAsset : fixedAssets) {
                fixedAssetIds.add(fixedAsset.getString("fixedAssetId"));
            }
            
            if (!fixedAssetIds.isEmpty()) {
                List<EntityCondition> giConditions = new ArrayList<>();
                giConditions.add(EntityCondition.makeCondition("productId", EntityOperator.IN, fixedAssetIds));
                giConditions.add(EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.EQUALS, "PLANT_TAG"));
                
                EntityCondition giCondition = EntityCondition.makeCondition(giConditions, EntityOperator.AND);
                
                List<GenericValue> goodIdentifications = EntityQuery.use(delegator)
                        .from("GoodIdentification")
                        .where(giCondition)
                        .queryList();
                
                Debug.logInfo("GoodIdentifications PLANT_TAG encontrados: " + goodIdentifications.size(), module);
                
                for (GenericValue gi : goodIdentifications) {
                    if ("PLANT_TAG".equals(gi.getString("goodIdentificationTypeId"))) {
                        plantTags.put(gi.getString("productId"), gi.getString("idValue"));
                        Debug.logInfo("PLANT_TAG - FixedAssetId: " + gi.getString("productId") + " -> MachineId: " + gi.getString("idValue"), module);
                    }
                }
            }
            
            // 6. Construir resultados finales aplicando filtro de machineId
            for (GenericValue fixedAsset : fixedAssets) {
                String assetId = fixedAsset.getString("fixedAssetId");
                String facId = fixedAsset.getString("locatedAtFacilityId");
                String facName = facilityNames.get(facId);
                String plantTag = plantTags.get(assetId);
                
                Debug.logInfo("Procesando - AssetId: " + assetId + " - Facility: " + facName + " - PlantTag: " + plantTag, module);

                // Aplicar filtro por machineId (PLANT_TAG) si se especificó
                if (machineId != null && !machineId.isEmpty()) {
                    if (plantTag == null || !plantTag.toLowerCase().contains(machineId.toLowerCase())) {
                    	Debug.logInfo("Filtrando - AssetId: " + assetId + " no cumple filtro machineId", module);
                        continue;
                    }
                }
                
                Map<String, String> resultRow = createResultRow(fixedAsset, plantTag, facName);
                results.add(resultRow);
                //results.add(createResultRow(fixedAsset, plantTag, facName));
                
                Debug.logInfo("Resultado añadido: " + resultRow, module);
            }
            
            Debug.logInfo("=== BÚSQUEDA COMPLETADA - Total resultados: " + results.size() + " ===", module);
            
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error searching machinery: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error searching machinery: " + e.getMessage());
        }

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("machineryList", results);
        return result;
    }

    // Método auxiliar para crear una fila de resultado
    private static Map<String, String> createResultRow(GenericValue fixedAsset, String plantTag, String facilityName) {
        Map<String, String> row = new HashMap<>();
        row.put("machineId", plantTag != null ? plantTag : "");
        row.put("assetId", fixedAsset.getString("fixedAssetId"));
        row.put("name", fixedAsset.getString("fixedAssetName"));
        row.put("facilityName", facilityName != null ? facilityName : "");
        row.put("machineType", fixedAsset.getString("fixedAssetTypeId"));
        return row;
    }

    public static Map<String, Object> getMachineryReferenceData(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Obtener todas las facilities
            List<GenericValue> facilities = EntityQuery.use(delegator)
                    .from("Facility")
                    .queryList();
            result.put("facilities", facilities);
            
            // Obtener todos los tipos de fixed asset
            List<GenericValue> fixedAssetTypes = EntityQuery.use(delegator)
                    .from("FixedAssetType")
                    .queryList();
            result.put("fixedAssetTypes", fixedAssetTypes);
            
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error getting reference data: " + e.getMessage(), module);
        }
        
        return result;
    }
    /*
    public static Map<String, Object> createMachinery(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        
        try {
            // 1. Extraer parámetros del formulario
            String facilityId = (String) context.get("facilityId");
            String machineId = (String) context.get("machineId"); // Este es el PLANT_TAG
            String fixedAssetName = (String) context.get("fixedAssetName");
            String fixedAssetTypeId = (String) context.get("fixedAssetTypeId");
            String description = (String) context.get("description");
            String serialNumber = (String) context.get("serialNumber");
            
            // 2. Validaciones básicas
            if (UtilValidate.isEmpty(machineId)) {
                result.put("errorMessage", "Machine ID (PLANT_TAG) es requerido");
                return result;
            }
            
            if (UtilValidate.isEmpty(fixedAssetName)) {
                result.put("errorMessage", "Nombre del activo es requerido");
                return result;
            }
            
            // 3. Verificar si ya existe el PLANT_TAG en GoodIdentification
            List<GenericValue> existingPlantTags = EntityQuery.use(delegator)
                    .from("GoodIdentification")
                    .where(EntityCondition.makeCondition(
                        EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.EQUALS, "PLANT_TAG"),
                        EntityCondition.makeCondition("idValue", EntityOperator.EQUALS, machineId),
                        EntityOperator.AND
                    ))
                    .queryList();
            
            if (!existingPlantTags.isEmpty()) {
                result.put("errorMessage", "Ya existe una máquina con PLANT_TAG: " + machineId);
                return result;
            }
            
            // 4. CREAR FIXED ASSET (Entidad principal)
            String fixedAssetId = delegator.getNextSeqId("FixedAsset");
            
            GenericValue fixedAsset = delegator.makeValue("FixedAsset");
            fixedAsset.set("fixedAssetId", fixedAssetId);
            fixedAsset.set("fixedAssetName", fixedAssetName);
            fixedAsset.set("fixedAssetTypeId", fixedAssetTypeId);
            fixedAsset.set("description", description);
            fixedAsset.set("serialNumber", serialNumber);
            fixedAsset.set("dateAcquired", new java.sql.Timestamp(System.currentTimeMillis()));
            
            // Asignar Facility si se proporcionó
            if (UtilValidate.isNotEmpty(facilityId)) {
                // Verificar que la Facility existe
                GenericValue facility = EntityQuery.use(delegator)
                        .from("Facility")
                        .where("facilityId", facilityId)
                        .queryOne();
                if (facility != null) {
                    fixedAsset.set("locatedAtFacilityId", facilityId);
                } else {
                    result.put("warningMessage", "La Facility especificada no existe, se creará sin ubicación");
                }
            }
            
            // Crear el FixedAsset
            fixedAsset = delegator.create(fixedAsset);
            
            // 5. CREAR GOOD IDENTIFICATION (PLANT_TAG)
            GenericValue goodIdentification = delegator.makeValue("GoodIdentification");
            goodIdentification.set("goodIdentificationTypeId", "PLANT_TAG");
            goodIdentification.set("productId", fixedAssetId); // Relacionar con el FixedAsset
            goodIdentification.set("idValue", machineId); // El Machine ID del formulario
            
            delegator.create(goodIdentification);
            
            // 6. OPCIONAL: Si se necesita, crear también identificación con serial number
            if (UtilValidate.isNotEmpty(serialNumber)) {
                GenericValue serialIdentification = delegator.makeValue("GoodIdentification");
                serialIdentification.set("goodIdentificationTypeId", "SERIAL_NUMBER");
                serialIdentification.set("productId", fixedAssetId);
                serialIdentification.set("idValue", serialNumber);
                
                delegator.create(serialIdentification);
            }
            
            // 7. Retornar resultado exitoso
            result.put("fixedAssetId", fixedAssetId);
            result.put("machineId", machineId);
            result.put("successMessage", "Máquina creada exitosamente con Asset ID: " + fixedAssetId + " y Machine ID: " + machineId);
            
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error creando maquinaria", module);
            result.put("errorMessage", "Error al crear la máquina: " + e.getMessage());
        }
        
        return result;
    }
    */
    /*
    public static Map<String, Object> createMachinery(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        
        try {
            // 1. Extraer parámetros del formulario
            String facilityName = (String) context.get("facilityName");
            String machineId = (String) context.get("machineId");
            String fixedAssetId = (String) context.get("fixedAssetId");
            String name = (String) context.get("name");
            String machineTypeId = (String) context.get("machineTypeId");
            
            // 2. Validaciones básicas
            if (UtilValidate.isEmpty(machineId)) {
                result.put("errorMessage", "Machine ID es requerido");
                return result;
            }
            
            // 3. Verificar si ya existe
            GenericValue existingMachinery = delegator.findOne("Machinery", 
                UtilMisc.toMap("machineId", machineId), false);
            if (existingMachinery != null) {
                result.put("errorMessage", "Ya existe una máquina con ID: " + machineId);
                return result;
            }
            
            // 4. Crear el registro
            GenericValue machinery = delegator.makeValue("Machinery");
            machinery.set("facilityName", facilityName);
            machinery.set("machineId", machineId);
            machinery.set("fixedAssetId", fixedAssetId);
            machinery.set("name", name);
            machinery.set("machineTypeId", machineTypeId);
            
            // 5. Guardar en base de datos
            machinery = delegator.create(machinery);
            
            // 6. Retornar resultado exitoso
            result.put("machineryId", machinery.getString("machineId"));
            result.put("successMessage", "Máquina creada exitosamente");
            
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error creando maquinaria", module);
            result.put("errorMessage", "Error al crear la máquina: " + e.getMessage());
        }
        
        return result;
    }
    */
    public static Map<String, Object> deleteMachinery(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String fixedAssetId = (String) context.get("fixedAssetId");
        
        try {
            GenericValue fixedAsset = EntityQuery.use(delegator)
                    .from("FixedAsset")
                    .where("fixedAssetId", fixedAssetId)
                    .queryOne();
                    
            if (fixedAsset != null) {
                fixedAsset.set("actualEndOfLife", new java.sql.Timestamp(System.currentTimeMillis()));
                fixedAsset.store();
            }
            
            return ServiceUtil.returnSuccess();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error deleting machinery: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error deleting machinery: " + e.getMessage());
        }
    }
}