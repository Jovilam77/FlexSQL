package cn.vonce.sql.json;

import cn.vonce.sql.uitls.BeanUtil;
import cn.vonce.sql.uitls.DateUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * JSON解析器
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/11/22 19:04
 */
public class JSONParser {

    public static Object parse(String json) throws Exception {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("JSON string is null or empty");
        }
        json = json.trim();
        return parseValue(new JSONReader(json));
    }

    private static Object parseValue(JSONReader reader) throws Exception {
        reader.skipWhitespace();
        char current = reader.peek();
        switch (current) {
            case '{':
                return parseObject(reader);
            case '[':
                return parseArray(reader);
            case '"':
                return parseString(reader);
            case 'n':
                reader.expect("null");
                return null;
            case 't':
                reader.expect("true");
                return true;
            case 'f':
                reader.expect("false");
                return false;
            default:
                return parseNumber(reader);
        }
    }

    private static JSONObject parseObject(JSONReader reader) throws Exception {
        JSONObject jsonObject = new JSONObject();
        reader.expect('{');
        reader.skipWhitespace();

        while (reader.peek() != '}') {
            String key = parseString(reader);
            reader.skipWhitespace();
            reader.expect(':');
            Object value = parseValue(reader);
            jsonObject.put(key, value);
            reader.skipWhitespace();

            if (reader.peek() == ',') {
                reader.next();
                reader.skipWhitespace();
            } else {
                break;
            }
        }
        reader.expect('}');
        return jsonObject;
    }

    private static JSONArray parseArray(JSONReader reader) throws Exception {
        JSONArray jsonArray = new JSONArray();
        reader.expect('[');
        reader.skipWhitespace();

        while (reader.peek() != ']') {
            Object value = parseValue(reader);
            jsonArray.add(value);
            reader.skipWhitespace();

            if (reader.peek() == ',') {
                reader.next();
                reader.skipWhitespace();
            } else {
                break;
            }
        }
        reader.expect(']');
        return jsonArray;
    }

    private static String parseString(JSONReader reader) throws Exception {
        reader.expect('"');
        StringBuilder sb = new StringBuilder();

        while (true) {
            char c = reader.next();
            if (c == '"') {
                break;
            } else if (c == '\\') {
                // Handle escape sequences
                char escaped = reader.next();
                switch (escaped) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        String hex = reader.read(4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid escape sequence: \\" + escaped);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Number parseNumber(JSONReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();

        while (Character.isDigit(reader.peek()) || "-+eE.".indexOf(reader.peek()) >= 0) {
            sb.append(reader.next());
        }

        String numberStr = sb.toString();
        if (numberStr.contains(".") || numberStr.contains("e") || numberStr.contains("E")) {
            return Double.parseDouble(numberStr);
        } else {
            return Long.parseLong(numberStr);
        }
    }

    public static JSONArray parseArray(String json) {
        try {
            return (JSONArray) parse(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        try {
            Object object = parse(json);
            if (!(object instanceof JSONArray)) {
                throw new IllegalArgumentException("JSON is not a JSONArray");
            }
            List<Object> list = (List<Object>) object;
            if (clazz != null) {
                List<T> resultList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof JSONObject) {
                        JSONObject jsonObject = (JSONObject) item;
                        resultList.add(BeanUtil.toBean(clazz, jsonObject));
                    } else {
                        resultList.add((T) item);
                    }
                }
                return resultList;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static JSONObject parseObject(String json) {
        try {
            return (JSONObject) parse(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T parseObject(String json, Class<T> clazz) {
        try {
            Object object = parse(json);
            if (!(object instanceof JSONObject)) {
                throw new IllegalArgumentException("JSON is not a JSONObject");
            }
            if (clazz != null) {
                return BeanUtil.toBean(clazz, (Map<String, Object>) object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String toJSONString(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String || obj instanceof Character || obj instanceof Enum) {
            return "\"" + escapeString(obj.toString()) + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean || obj instanceof BigDecimal) {
            return obj.toString();
        } else if (obj instanceof Date || obj instanceof LocalDate || obj instanceof LocalDateTime || obj instanceof LocalTime) {
            return "\"" + DateUtil.unifyDateToString(obj) + "\"";
        } else if (obj instanceof Map) {
            return mapToJSONString((Map<?, ?>) obj);
        } else if (obj instanceof Collection) {
            return collectionToJSONString((Collection<?>) obj);
        } else if (obj.getClass().isArray()) {
            return arrayToJSONString(obj);
        } else {
            return mapToJSONString(BeanUtil.toMap(obj));
        }
    }

    private static String mapToJSONString(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(toJSONString(entry.getKey().toString())); // Map keys are always strings in JSON
            sb.append(":");
            sb.append(toJSONString(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String collectionToJSONString(Collection<?> collection) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(toJSONString(item));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String arrayToJSONString(Object array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(toJSONString(java.lang.reflect.Array.get(array, i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeString(String str) {
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // Only escape control characters, directly append others
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }


    public static void main(String[] args) {
//        String json = "{ \"name\": \"John\", \"age\": 30, \"isStudent\": false, \"grades\": [90, 85.5, \"A\"], \"address\": {\"city\": \"New York\", \"zip\": \"10001\"} }";
//        String json = "[1,2,3]";
        String json2 = "{\"resultcode\":\"200\",\"reason\":\"成功的返回\",\"result\":{\"company\":\"顺丰\",\"com\":\"sf\",\"no\":\"575677355677\",\"list\":[{\"datetime\":\"2013-06-25 10:44:05\",\"remark\":\"已收件\",\"zone\":\"台州市\"},{\"datetime\":\"2013-06-25 11:05:21\",\"remark\":\"快件在台州,准备送往下一站台州集散中心\",\"zone\":\"台州市\"}],\"status\":1 },\"error_code\":0 }";
        String json3 = "[{\"datetime\":\"2013-06-25 10:44:05\",\"zone\":\"台州市\",\"remark\":\"已收件\"},{\"datetime\":\"2013-06-25 11:05:21\",\"zone\":\"台州市\",\"remark\":\"快件在台州,准备送往下一站台州集散中心\"}]";
        try {
            Object result = parse(json3);
            System.out.println(result);
            System.out.println(result.getClass());
            JSONObject jsonObject = parseObject(json2);
            System.out.println(jsonObject.toJSONString(jsonObject));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
