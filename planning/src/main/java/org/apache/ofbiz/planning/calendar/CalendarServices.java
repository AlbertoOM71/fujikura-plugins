package org.apache.ofbiz.planning.calendar;

import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

import java.util.*;

public class CalendarServices {
    
    public static Map<String, Object> getCalendarYearData(DispatchContext dctx, Map<String, ? extends Object> context) {
        String calendarId = (String) context.get("calendarId");
        Object yearObj = context.get("year");
        Integer year = null;
        
        // Convertir year a Integer si viene como String del formulario
        if (yearObj instanceof String) {
            try {
                year = Integer.parseInt((String) yearObj);
            } catch (NumberFormatException e) {
                // Si no es un número válido, dejarlo como null
            }
        } else if (yearObj instanceof Integer) {
            year = (Integer) yearObj;
        }
        
        System.out.println("=== DEBUG CalendarServices ===");
        System.out.println("calendarId recibido: " + calendarId);
        System.out.println("year recibido: " + yearObj + " (tipo: " + (yearObj != null ? yearObj.getClass().getSimpleName() : "null") + ")");
        System.out.println("year convertido: " + year);
        
        try {
            // Obtener lista de calendarios disponibles
            List<GenericValue> calendarList = EntityQuery.use(dctx.getDelegator())
                    .from("TechDataCalendar")
                    .queryList();
            
            System.out.println("Calendarios encontrados: " + (calendarList != null ? calendarList.size() : "null"));
            
            Map<String, Object> result = ServiceUtil.returnSuccess();
            
            // SIEMPRE devolver todos los parámetros OUT definidos en services.xml
            result.put("calendarList", calendarList != null ? calendarList : new ArrayList<>());
            
            // Procesar parámetros del formulario - asegurarse de devolver siempre los valores
            if (calendarId != null && !calendarId.isEmpty() && year != null) {
                result.put("selectedCalendarId", calendarId);
                result.put("selectedYear", year);
                result.put("yearCalendar", new HashMap<String, Object>());
                
                System.out.println("Parámetros procesados: calendarId=" + calendarId + ", year=" + year);
            } else {
                // VALORES POR DEFECTO - IMPORTANTE: siempre devolver todos los parámetros OUT
                result.put("selectedCalendarId", calendarId != null ? calendarId : "");
                result.put("selectedYear", year != null ? year : 0); // Usar 0 como valor por defecto para Integer
                result.put("yearCalendar", new HashMap<String, Object>());
                
                System.out.println("Usando valores por defecto");
            }
            
            System.out.println("Resultado devuelto:");
            System.out.println(" - calendarList: " + result.get("calendarList"));
            System.out.println(" - selectedCalendarId: " + result.get("selectedCalendarId"));
            System.out.println(" - selectedYear: " + result.get("selectedYear"));
            System.out.println(" - yearCalendar: " + result.get("yearCalendar"));
            System.out.println("=== FIN DEBUG ===");
            
            return result;
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            
            // Incluso en error, devolver la estructura esperada
            Map<String, Object> errorResult = ServiceUtil.returnError("Error loading calendar data: " + e.getMessage());
            errorResult.put("calendarList", new ArrayList<>());
            errorResult.put("selectedCalendarId", "");
            errorResult.put("selectedYear", 0);
            errorResult.put("yearCalendar", new HashMap<String, Object>());
            
            return errorResult;
        }
    }
}