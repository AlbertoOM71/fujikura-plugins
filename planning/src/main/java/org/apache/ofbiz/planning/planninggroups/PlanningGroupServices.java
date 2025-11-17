package org.apache.ofbiz.planning.planninggroups;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

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
            if (projectCategoryId != null && !projectCategoryId.isEmpty()) {
                conditions.add(EntityCondition.makeCondition(
                        "primaryParentCategoryId",
                        EntityOperator.EQUALS,
                        projectCategoryId
                ));
            }

            // Filtro por ID de grupo (búsqueda parcial sobre productCategoryId)
            if (planningGroupId != null && !planningGroupId.isEmpty()) {
                conditions.add(EntityCondition.makeCondition(
                        "productCategoryId",
                        EntityOperator.LIKE,
                        "%" + planningGroupId + "%"
                ));
            }

            // Filtro por nombre (búsqueda parcial)
            if (categoryName != null && !categoryName.isEmpty()) {
                conditions.add(EntityCondition.makeCondition(
                        "categoryName",
                        EntityOperator.LIKE,
                        "%" + categoryName + "%"
                ));
            }

            // Filtro por descripción (búsqueda parcial)
            if (description != null && !description.isEmpty()) {
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

            // Procesar resultados: enriquecemos con datos del proyecto padre
            for (GenericValue category : planningGroups) {
                String parentId = category.getString("primaryParentCategoryId");
                GenericValue parentProject = null;

                if (parentId != null && !parentId.isEmpty()) {
                    parentProject = EntityQuery.use(delegator)
                            .from("ProductCategory")
                            .where("productCategoryId", parentId)
                            .cache()
                            .queryOne();
                }

                results.add(createResultRow(category, parentProject));
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
     * Crea una fila de resultado similar a createResultRow de MachineryServices,
     * pero basada en ProductCategory.
     */
    private static Map<String, String> createResultRow(GenericValue category, GenericValue project) {
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

        return row;
    }

    /**
     * Datos de referencia para pantallas de Planning Groups.
     *
     * OUT:
     *  - projects            (List<GenericValue>): categorías de tipo PROJECT
     *  - planningGroupTypes  (List<GenericValue>): tipos de categoría para PLANNING_GROUP
     */
    public static Map<String, Object> getPlanningGroupsReferenceData(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = new HashMap<>();

        try {
            // Proyectos (nivel superior) -> tipo PROJECT
            List<GenericValue> projects = EntityQuery.use(delegator)
                    .from("ProductCategory")
                    .where("productCategoryTypeId", "PROJECT")
                    .orderBy("categoryName")
                    .queryList();
            result.put("projects", projects);

            // Tipos de categoría para PLANNING_GROUP (normalmente 1, pero dejamos la puerta abierta)
            List<EntityCondition> typeConds = new ArrayList<>();
            typeConds.add(EntityCondition.makeCondition(
                    "productCategoryTypeId",
                    EntityOperator.EQUALS,
                    "PLANNING_GROUP"
            ));

            List<GenericValue> planningGroupTypes = EntityQuery.use(delegator)
                    .from("ProductCategoryType")
                    .where(EntityCondition.makeCondition(typeConds, EntityOperator.AND))
                    .queryList();
            result.put("planningGroupTypes", planningGroupTypes);

        } catch (GenericEntityException e) {
            Debug.logError(e, "Error getting planning groups reference data: " + e.getMessage(), module);
            // devolvemos success vacío, como hace mucho código de OFBiz con datos de referencia
        }

        return result;
    }
}
