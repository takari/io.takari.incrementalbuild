package io.takari.builder.apt;

import com.google.testing.compile.CompilationRule;
import io.takari.builder.internal.model.AbstractTypeAdapterTest;
import io.takari.builder.internal.model.MemberAdapter;
import java.util.Collection;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;

public class APTAdaptersTest extends AbstractTypeAdapterTest {

    @Rule
    public final CompilationRule compilation = new CompilationRule();

    private Elements elements;
    private Types types;
    private APT apt;

    @Before
    public void setup() {
        elements = compilation.getElements();
        types = compilation.getTypes();
        apt = new APT(elements, types);
    }

    @Override
    protected Collection<MemberAdapter> listMembers(Class<?> type) {
        TypeElement element = elements.getTypeElement(type.getCanonicalName());
        return apt.getAllMembers(element);
    }

    //
    // Actual tests are defined in AbstractTypeAdapterTest and shared with APT
    //

}
