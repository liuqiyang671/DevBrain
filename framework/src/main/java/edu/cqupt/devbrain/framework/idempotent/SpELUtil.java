package edu.cqupt.devbrain.framework.idempotent;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * SpEL（Spring Expression Language）表达式解析工具类
 * <p>
 * 提供 SpEL 表达式的解析功能，主要用于幂等注解中自定义 Key 的动态生成。
 * 支持两种表达式形式：
 * <ul>
 *   <li>参数引用表达式 - 如 {@code #paramName}，引用方法参数的值</li>
 *   <li>类型表达式 - 如 {@code T(java.lang.System)}，引用 Java 类型</li>
 * </ul>
 * </p>
 * <p>
 * 如果传入的表达式不包含 SpEL 标记（# 或 T(），则直接返回原始字符串。
 * </p>
 */
public final class SpELUtil {

    /** 参数名发现器，用于获取方法参数名称 */
    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /** SpEL 表达式解析器 */
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /**
     * 解析 Key 表达式
     * <p>
     * 判断传入的字符串是否为 SpEL 表达式（包含 # 或 T( 标记）：
     * <ul>
     *   <li>如果是 SpEL 表达式，调用 {@link #parse} 方法解析并返回结果</li>
     *   <li>如果不是 SpEL 表达式，直接返回原始字符串</li>
     * </ul>
     * </p>
     *
     * @param spEl       待解析的字符串，可能是普通字符串或 SpEL 表达式
     * @param method     当前执行的方法对象
     * @param contextObj 方法参数值数组
     * @return 解析后的结果，可能是 SpEL 表达式的求值结果或原始字符串
     */
    public static Object parseKey(String spEl, Method method, Object[] contextObj) {
        List<String> spELFlag = ListUtil.of("#", "T(");
        Optional<String> optional = spELFlag.stream().filter(spEl::contains).findFirst();
        if (optional.isPresent()) {
            return parse(spEl, method, contextObj);
        }
        return spEl;
    }

    /**
     * 解析 SpEL 表达式并返回求值结果
     * <p>
     * 使用 Spring 的 {@link ExpressionParser} 解析 SpEL 表达式，并将方法参数绑定到求值上下文中，
     * 使表达式可以通过 {@code #paramName} 语法引用方法参数的值。
     * </p>
     *
     * @param spEl       SpEL 表达式字符串
     * @param method     当前执行的方法对象，用于获取参数名称
     * @param contextObj 方法参数值数组
     * @return SpEL 表达式求值结果
     */
    public static Object parse(String spEl, Method method, Object[] contextObj) {
        Expression exp = EXPRESSION_PARSER.parseExpression(spEl);
        String[] params = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (ArrayUtil.isNotEmpty(params)) {
            for (int len = 0; len < params.length; len++) {
                context.setVariable(params[len], contextObj[len]);
            }
        }
        return exp.getValue(context);
    }
}
