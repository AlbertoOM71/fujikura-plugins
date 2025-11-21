package org.apache.ofbiz.planning.planninggroups;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanningGroupServices {

    public static final String module = PlanningGroupServices.class.getName();

    /**
     * Búsqueda de grupos de planificación.
     *
     * IN:
     *  - facilityId        (String, opcional): filtro por instalación
     *  - projectCategoryId (String, opcional): proyecto padre (ProductCategoryId de tipo PROJECT)
     *  - planningGroupId   (String, opcional): filtro parcial por ID de grupo (productCategoryId)
     *  - categoryName      (String, opcional): filtro parcial por nombre
     *  - description       (String, opcional): filtro parcial por descripción
     *
     * OUT:
     *  - planningGroupList (List<Map<String,String>>)
     */
    public static Map<String, Object> searchPlanningGroups(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        List<Map<String, String>> results = new ArrayList<>();

        String facilityId        = (String) context.get("facilityId");
        String projectCategoryId = (String) context.get("projectCategoryId");
        String planningGroupId   = (String) context.get("planningGroupId");
        String categoryName      = (String) context.get("categoryName");
        String description       = (String) context.get("description");

        try {
            // Construir condiciones dinámicamente
            List<EntityCondition> conditions = new ArrayList<>();

            // Siempre filtramos por tipo PLANNING_GROUP
            conditions.add(EntityCondition.makeCondition(
                    "productCategoryTypeId",
                    EntityOperator.EQUALS,
                    "PLANNING_GROUP"
            ));

            // Filtro por proyecto padre (jerarquía Proyecto -> Grupo de planificación)
            if (UtilValidate.isNotEmpty(projectCategoryId)) {
                conditions.add(EntityCondition.makeCondition(
                        "primaryParentCategoryId",
                        EntityOperator.EQUALS,
                        projectCategoryId
                ));
            }

            // Filtro por ID de grupo (búsqueda parcial sobre productCategoryId)
            if (UtilValidate.isNotEmpty(planningGroupId)) {
                conditions.add(EntityCondition.makeCondition(
                        "productCategoryId",
                        EntityOperator.LIKE,
                        "%" + planningGroupId + "%"
                ));
            }

            // Filtro por nombre (búsqueda parcial)
            if (UtilValidate.isNotEmpty(categoryName)) {
                conditions.add(EntityCondition.makeCondition(
                        "categoryName",
                        EntityOperator.LIKE,
                        "%" + categoryName + "%"
                ));
            }

            // Filtro por descripción (búsqueda parcial)
            if (UtilValidate.isNotEmpty(description)) {
                conditions.add(EntityCondition.makeCondition(
                        "description",
                        EntityOperator.LIKE,
                        "%" + description + "%"
                ));
            }

            // Ejecutar consulta base en ProductCategory
            EntityCondition condition = conditions.isEmpty()
                    ? null
                    : EntityCondition.makeCondition(conditions, EntityOperator.AND);

            List<GenericValue> planningGroups = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where(condition)
                    .orderBy("primaryParentCategoryId", "categoryName")
                    .queryList();

            // Procesar resultados: enriquecemos con datos del proyecto padre y facility
            for (GenericValue category : planningGroups) {
                String parentId = category.getString("primaryParentCategoryId");
                GenericValue parentProject = null;
                GenericValue facility = null;

                // Obtener proyecto padre
                if (UtilValidate.isNotEmpty(parentId)) {
                    parentProject = EntityQuery.use(delegator)
                            .from("ProductCategory")
                            .where("productCategoryId", parentId)
                            .cache()
                            .queryOne();
                }

                // Obtener facility asociada al grupo (a través de ProductCategoryAttribute)
                GenericValue facilityAttr = EntityQuery.use(delegator)
                        .from("ProductCategoryAttribute")
                        .where("productCategoryId", category.getString("productCategoryId"),
                               "attrName", "FACILITY_ID")
                        .cache()
                        .queryFirst();

                if (facilityAttr != null) {
                    String categoryFacilityId = facilityAttr.getString("attrValue");
                    
                    // Aplicar filtro de facility si se especificó
                    if (UtilValidate.isNotEmpty(facilityId) && !facilityId.equals(categoryFacilityId)) {
                        continue; // Saltar este registro si no coincide con el filtro de facility
                    }
                    
                    if (UtilValidate.isNotEmpty(categoryFacilityId)) {
                        facility = EntityQuery.use(delegator)
                                .from("Facility")
                                .where("facilityId", categoryFacilityId)
                                .cache()
                                .queryOne();
                    }
                }

                results.add(createResultRow(category, parentProject, facility));
            }

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error searching planning groups: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error searching planning groups: " + e.getMessage());
        }

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("planningGroupList", results);
        return result;
    }

    /**
     * Crea una fila de resultado con datos del grupo, proyecto y facility.
     */
    private static Map<String, String> createResultRow(GenericValue category, GenericValue project, GenericValue facility) {
        Map<String, String> row = new HashMap<>();

        // Datos propios del grupo de planificación
        row.put("productCategoryId",       category.getString("productCategoryId"));
        row.put("productCategoryTypeId",   category.getString("productCategoryTypeId"));
        row.put("primaryParentCategoryId", category.getString("primaryParentCategoryId"));
        row.put("categoryName",            category.getString("categoryName"));
        row.put("description",             category.getString("description"));

        // Datos del proyecto padre (si existe)
        if (project != null) {
            row.put("projectCategoryId", project.getString("productCategoryId"));
            row.put("projectName",       project.getString("categoryName"));
        } else {
            row.put("projectCategoryId", "");
            row.put("projectName",       "");
        }

        // Datos de facility (si existe)
        if (facility != null) {
            row.put("facilityId",   facility.getString("facilityId"));
            row.put("facilityName", facility.getString("facilityName"));
        } else {
            row.put("facilityId",   "");
            row.put("facilityName", "");
        }

        return row;
    }

    /**
     * Datos de referencia para pantallas de Planning Groups.
     *
     * OUT:
     *  - projects            (List<GenericValue>): categorías de tipo PROJECT
     *  - facilities          (List<GenericValue>): lista de facilities
     *  - planningGroupTypes  (List<GenericValue>): tipos de categoría para PLANNING_GROUP
     */
    public static Map<String, Object> getPlanningGroupsReferenceData(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();

        // SIEMPRE inicializar con listas vacías
        List<GenericValue> projects = new ArrayList<>();
        List<GenericValue> facilities = new ArrayList<>();
        List<GenericValue> planningGroupTypes = new ArrayList<>();

        try {
            // Proyectos (nivel superior) -> tipo PROJECT
            projects = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryTypeId", "PROJECT")
                    .orderBy("categoryName")
                    .queryList();
            if (projects == null) projects = new ArrayList<>();
            
            // Facilities
            facilities = EntityQuery.use(delegator)
                    .from("Facility")
                    .orderBy("facilityName")
                    .queryList();
            if (facilities == null) facilities = new ArrayList<>();
            
            // Tipos de categoría para PLANNING_GROUP
            planningGroupTypes = EntityQuery.use(delegator)
                    .from("ProductCategoryType")
                    .where("productCategoryTypeId", "PLANNING_GROUP")
                    .queryList();
            if (planningGroupTypes == null) planningGroupTypes = new ArrayList<>();

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error getting planning groups reference data: " + e.getMessage(), module);
            // Las listas ya están inicializadas como vacías
        }

        // SIEMPRE agregar los parámetros
        result.put("projects", projects);
        result.put("facilities", facilities);
        result.put("planningGroupTypes", planningGroupTypes);

        return result;
    }

    /**
     * Crear nuevo grupo de planificación.
     *
     * IN:
     *  - productCategoryId       (String): ID del grupo (requerido)
     *  - categoryName            (String): Nombre del grupo (requerido)
     *  - description             (String, opcional): Descripción
     *  - primaryParentCategoryId (String): ID del proyecto padre (requerido)
     *  - facilityId              (String): ID de la facility (requerido)
     *
     * OUT:
     *  - productCategoryId (String): ID del grupo creado
     */
    public static Map<String, Object> createPlanningGroup(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();

        String productCategoryId       = (String) context.get("productCategoryId");
        String categoryName            = (String) context.get("categoryName");
        String description             = (String) context.get("description");
        String primaryParentCategoryId = (String) context.get("primaryParentCategoryId");
        String facilityId              = (String) context.get("facilityId");

        try {
            // Validaciones
            if (UtilValidate.isEmpty(productCategoryId)) {
                return ServiceUtil.returnError("Planning Group ID is required");
            }
            if (UtilValidate.isEmpty(categoryName)) {
                return ServiceUtil.returnError("Planning Group Name is required");
            }
            if (UtilValidate.isEmpty(primaryParentCategoryId)) {
                return ServiceUtil.returnError("Project is required");
            }
            if (UtilValidate.isEmpty(facilityId)) {
                return ServiceUtil.returnError("Facility is required");
            }

            // Verificar que no exista ya un grupo con ese ID
            GenericValue existing = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryId", productCategoryId)
                    .queryOne();

            if (existing != null) {
                return ServiceUtil.returnError("Planning Group ID already exists: " + productCategoryId);
            }

            // Crear ProductCategory
            GenericValue planningGroup = delegator.makeValue("ProductCategory");
            planningGroup.set("productCategoryId", productCategoryId);
            planningGroup.set("productCategoryTypeId", "PLANNING_GROUP");
            planningGroup.set("categoryName", categoryName);
            planningGroup.set("description", description);
            planningGroup.set("primaryParentCategoryId", primaryParentCategoryId);
            
            planningGroup.create();

            // Asociar facility mediante ProductCategoryAttribute
            GenericValue facilityAttr = delegator.makeValue("ProductCategoryAttribute");
            facilityAttr.set("productCategoryId", productCategoryId);
            facilityAttr.set("attrName", "FACILITY_ID");
            facilityAttr.set("attrValue", facilityId);
            
            facilityAttr.create();

            Debug.logInfo("Created Planning Group: " + productCategoryId, module);

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error creating planning group: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error creating planning group: " + e.getMessage());
        }

        Map<String, Object> result = ServiceUtil.returnSuccess("Planning Group created successfully");
        result.put("productCategoryId", productCategoryId);
        return result;
    }

    /**
     * Actualizar grupo de planificación existente.
     *
     * IN:
     *  - productCategoryId       (String): ID del grupo (requerido)
     *  - categoryName            (String): Nombre del grupo
     *  - description             (String): Descripción
     *  - primaryParentCategoryId (String): ID del proyecto padre
     *  - facilityId              (String): ID de la facility
     *
     * OUT:
     *  - productCategoryId (String): ID del grupo actualizado
     */
    public static Map<String, Object> updatePlanningGroup(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();

        String productCategoryId       = (String) context.get("productCategoryId");
        String categoryName            = (String) context.get("categoryName");
        String description             = (String) context.get("description");
        String primaryParentCategoryId = (String) context.get("primaryParentCategoryId");
        String facilityId              = (String) context.get("facilityId");

        try {
            // Validación
            if (UtilValidate.isEmpty(productCategoryId)) {
                return ServiceUtil.returnError("Planning Group ID is required");
            }

            // Buscar el grupo existente
            GenericValue planningGroup = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryId", productCategoryId)
                    .queryOne();

            if (planningGroup == null) {
                return ServiceUtil.returnError("Planning Group not found: " + productCategoryId);
            }

            // Actualizar campos
            if (UtilValidate.isNotEmpty(categoryName)) {
                planningGroup.set("categoryName", categoryName);
            }
            if (description != null) {
                planningGroup.set("description", description);
            }
            if (UtilValidate.isNotEmpty(primaryParentCategoryId)) {
                planningGroup.set("primaryParentCategoryId", primaryParentCategoryId);
            }

            planningGroup.store();

            // Actualizar facility si se proporciona
            if (UtilValidate.isNotEmpty(facilityId)) {
                GenericValue facilityAttr = EntityQuery.use(delegator)
                        .from("ProductCategoryAttribute")
                        .where("productCategoryId", productCategoryId,
                               "attrName", "FACILITY_ID")
                        .queryFirst();

                if (facilityAttr != null) {
                    facilityAttr.set("attrValue", facilityId);
                    facilityAttr.store();
                } else {
                    // Crear si no existe
                    facilityAttr = delegator.makeValue("ProductCategoryAttribute");
                    facilityAttr.set("productCategoryId", productCategoryId);
                    facilityAttr.set("attrName", "FACILITY_ID");
                    facilityAttr.set("attrValue", facilityId);
                    facilityAttr.create();
                }
            }

            Debug.logInfo("Updated Planning Group: " + productCategoryId, module);

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error updating planning group: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error updating planning group: " + e.getMessage());
        }

        Map<String, Object> result = ServiceUtil.returnSuccess("Planning Group updated successfully");
        result.put("productCategoryId", productCategoryId);
        return result;
    }

    /**
     * Eliminar grupo de planificación.
     *
     * IN:
     *  - productCategoryId (String): ID del grupo (requerido)
     *
     * OUT:
     *  - productCategoryId (String): ID del grupo eliminado
     */
    public static Map<String, Object> deletePlanningGroup(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();

        String productCategoryId = (String) context.get("productCategoryId");

        try {
            // Validación
            if (UtilValidate.isEmpty(productCategoryId)) {
                return ServiceUtil.returnError("Planning Group ID is required");
            }

            // Buscar el grupo
            GenericValue planningGroup = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryId", productCategoryId)
                    .queryOne();

            if (planningGroup == null) {
                return ServiceUtil.returnError("Planning Group not found: " + productCategoryId);
            }

            // Eliminar atributo de facility si existe
            List<GenericValue> attributes = EntityQuery.use(delegator)
                    .from("ProductCategoryAttribute")
                    .where("productCategoryId", productCategoryId)
                    .queryList();

            for (GenericValue attr : attributes) {
                attr.remove();
            }

            // Eliminar el grupo
            planningGroup.remove();

            Debug.logInfo("Deleted Planning Group: " + productCategoryId, module);

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error deleting planning group: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error deleting planning group: " + e.getMessage());
        }

        Map<String, Object> result = ServiceUtil.returnSuccess("Planning Group deleted successfully");
        result.put("productCategoryId", productCategoryId);
        return result;
    }

    /**
     * Obtener datos de un grupo de planificación específico para edición.
     *
     * IN:
     *  - productCategoryId (String): ID del grupo (requerido)
     *
     * OUT:
     *  - planningGroup (GenericValue): Datos del grupo
     *  - facilityId (String): ID de la facility asociada
     */
    public static Map<String, Object> getPlanningGroup(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();

        String productCategoryId = (String) context.get("productCategoryId");

        try {
            // Validación
            if (UtilValidate.isEmpty(productCategoryId)) {
                return ServiceUtil.returnError("Planning Group ID is required");
            }

            // Buscar el grupo
            GenericValue planningGroup = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryId", productCategoryId)
                    .queryOne();

            if (planningGroup == null) {
                return ServiceUtil.returnError("Planning Group not found: " + productCategoryId);
            }

            // Buscar facility asociada
            String facilityId = "";
            GenericValue facilityAttr = EntityQuery.use(delegator)
                    .from("ProductCategoryAttribute")
                    .where("productCategoryId", productCategoryId,
                           "attrName", "FACILITY_ID")
                    .queryFirst();

            if (facilityAttr != null) {
                facilityId = facilityAttr.getString("attrValue");
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("planningGroup", planningGroup);
            result.put("facilityId", facilityId);
            return result;

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error getting planning group: " + e.getMessage(), module);
            return ServiceUtil.returnError("Error getting planning group: " + e.getMessage());
        }
    }
}