package cn.vonce.sql.json;

import java.util.List;
import java.util.logging.Logger;

/**
 * 默认的JSON转换器
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/11/25 19:06
 */
public class JSONConvertImpl implements JSONConvert {

    private static final Logger logger = Logger.getLogger(JSONConvertImpl.class.getName());

    @Override
    public Object parse(String json) {
        try {
            return JSONParser.parse(json);
        } catch (Exception e) {
            logger.warning("Failed to parse JSON: " + e.getMessage());
        }
        return null;
    }

    @Override
    public <T> T parseObject(String json, Class<T> clazz) {
        return JSONObject.parseObject(json, clazz);
    }

    @Override
    public <T> List<T> parseArray(String json, Class<T> clazz) {
        return JSONArray.parseArray(json, clazz);
    }

    @Override
    public String toJSONString(Object object) {
        return JSONParser.toJSONString(object);
    }

}
