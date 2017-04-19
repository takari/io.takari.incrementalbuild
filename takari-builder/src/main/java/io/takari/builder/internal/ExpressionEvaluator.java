package io.takari.builder.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Evaluates <code>${property}</code> expressions. Specifically,
 * 
 * <ul>
 * <li><code>$$</code> escape sequence is replaced with <code>$</code>
 * <li><code>${property}</code> is replaced with the property value, if the property is known.
 * Throws {@link ExpressionEvaluationException} if the property is not known.
 * </ul>
 */
public class ExpressionEvaluator {
  private final List<Function<String, String>> resolvers;

  /**
   * Returns result of expression evaluation. Throws ExpressionEvaluationException if any of
   * ${properties} is not known.
   */
  public String evaluate(String expression) throws ExpressionEvaluationException {
    StringBuilder result = new StringBuilder();

    int idx = 0;
    while (idx < expression.length() - 2) {
      int from = expression.indexOf('$', idx);
      if (from < 0) {
        break;
      }
      int to;
      String substitute;
      if (expression.charAt(from + 1) == '$') {
        to = from + 1;
        substitute = "$";
      } else if (expression.charAt(from + 1) == '{') {
        to = expression.indexOf("}", from);
        if (to < 0) {
          break;
        }
        substitute = substitute(expression.substring(from + 2, to));
      } else {
        break;
      }
      result.append(expression.substring(idx, from));
      result.append(substitute);
      idx = to + 1;
    }

    if (idx < expression.length()) {
      result.append(expression.substring(idx));
    }

    return result.toString();
  }

  private String substitute(String property) throws ExpressionEvaluationException {
    for (Function<String, String> resolver : resolvers) {
      String resolved = resolver.apply(property);
      if (resolved != null) {
        return resolved;
      }
    }
    throw new ExpressionEvaluationException("${" + property + "}");
  }

  public ExpressionEvaluator(List<Function<String, String>> resolvers) {
    for(Function<String, String> resolver: resolvers) {
      if (resolver == null) {
        throw new NullPointerException();
      }
    }
    this.resolvers = new ArrayList<>(resolvers);
  }
}
