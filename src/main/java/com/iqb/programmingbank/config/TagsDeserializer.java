package com.iqb.programmingbank.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 标签列表反序列化器
 */
public class TagsDeserializer extends JsonDeserializer<List<String>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode node = codec.readTree(jsonParser);
        
        // 如果是字符串类型
        if (node.isTextual()) {
            String text = node.asText();
            
            // 如果是空字符串，返回空列表
            if (text == null || text.isEmpty() || "[]".equals(text)) {
                return Collections.emptyList();
            }
            
            try {
                // 尝试解析JSON数组字符串
                if (text.startsWith("[") && text.endsWith("]")) {
                    return Arrays.asList(objectMapper.readValue(text, String[].class));
                }
                
                // 单个标签
                return Collections.singletonList(text);
            } catch (Exception e) {
                return Collections.singletonList(text);
            }
        }
        
        // 如果是数组，直接转换
        if (node.isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    result.add(element.asText());
                }
            }
            return result;
        }
        
        // 其他情况返回空列表
        return Collections.emptyList();
    }
} 