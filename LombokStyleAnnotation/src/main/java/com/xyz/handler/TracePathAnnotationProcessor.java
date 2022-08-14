
package com.xyz.handler;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Set;
import javax.tools.Diagnostic;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.xyz.annotation.TraceMethodInfo;

/**
 * Class to adding trace information on the call
 * Use lombok.log to trace out (TRACE level) method parameters and return values
 * and total execute time.
 */
@SupportedAnnotationTypes( "com.xyz.annotation.TraceMethodInfo" )
public class TracePathAnnotationProcessor extends AbstractProcessor {
    // 编译时期输入日志的
    private Messager messager;

    // 将Element转换为JCTree的工具,提供了待处理的抽象语法树
    private JavacTrees trees;

    // 封装了创建AST节点的一些方法
    private TreeMaker treeMaker;

    // 提供了创建标识符的方法
    private Names names;

    /**
     * if not provide this method, then output:
     * 警告: No SupportedSourceVersion annotation found on com.xyz.handler.TracePathAnnotationProcessor, returning RELEASE_6.
     * 警告: 来自注释处理程序 'com.xyz.handler.TracePathAnnotationProcessor' 的受支持 source 版本 'RELEASE_6' 低于 -source '1.8'
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init( processingEnv );
        this.messager = processingEnv.getMessager();
        trees = JavacTrees.instance( processingEnv );
        treeMaker = TreeMaker.instance( ((JavacProcessingEnvironment) processingEnv).getContext() );
        names = Names.instance( ((JavacProcessingEnvironment) processingEnv).getContext() );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(TraceMethodInfo.class);

        annotatedElements.forEach( element -> {
            showLog(" processing [" + element.toString() + "] " );
            if( element.getKind().equals( ElementKind.METHOD ) ) {
                showLog(" processing method: " + element.getSimpleName() );

                JCTree tree = trees.getTree(element);
                Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) element;

                // 重写 accept 方法，extend TreeTranslator类
                tree.accept(new TreeTranslator() {
                    @Override
                    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
                        super.visitMethodDef(jcMethodDecl);
                        showLog( " process " + jcMethodDecl.getName() + " via visitMethodDef " );

                        JCTree.JCBlock methodBody = jcMethodDecl.getBody();
                        if( methodBody == null ) {
                            showError( "Method " + jcMethodDecl.name + " dont have body!" );
                            return;
                        }

                        List<JCTree.JCVariableDecl> params = jcMethodDecl.params;

                        JCTree.JCStatement logPart = buildArgumentsLog( jcMethodDecl.name, params );
                        // move old body to new
                        ArrayList<JCTree.JCStatement> jsta = new ArrayList<>();
                        jsta.add( logPart );
                        for(JCTree.JCStatement stm : methodBody.getStatements() ) {
                            // 方法返回
                            if( stm instanceof JCTree.JCReturn ) {
                                System.out.println( "X" );
                            }
                            jsta.add( stm );
                        }

                        methodBody = treeMaker.Block( 0, List.from(jsta) );
                        jcMethodDecl.body = methodBody;
//                        showLog( methodBody.stats.toString() );
                    }
                });
            }
        } );

        return false;
    }

    /**
     * build a new method statement to replace the old one.
     * @param name
     * @param params
     * @return new method statement with log code added.
     *          format: "<method-name> ( <params(0)-name>=<params(0)-value>, <params(1)-name>=<params(1)-value>,
     *          ..., <params(n)-name>=<params(n)-value> )"
     *          in case no parameter, format: "<method-name> ()"
     */
    private JCTree.JCStatement buildArgumentsLog(Name name, List<JCTree.JCVariableDecl> params) {
        JCTree.JCFieldAccess debugMethod = getDebugMethod();
        // only one parameter, then set array size = 1
        JCTree.JCExpression[] jcExpressions = new JCTree.JCExpression[ 1 ];
        // no parameters for the method
        if( params.isEmpty() ) {
            // parameter of calling method: log.debug( "method-name()" );
            jcExpressions[0] = treeMaker.Literal( name.toString() + "()" );
        } else {
            jcExpressions[0] = generateParametersString( name, params );
        }

        // each steps with return value type
        List<JCTree.JCExpression> debugParams = List.from(jcExpressions);
        JCTree.JCMethodInvocation debugMethodInvocation = treeMaker.App( debugMethod, debugParams);
        JCTree.JCExpressionStatement exec = treeMaker.Exec(debugMethodInvocation);

        showLog( "Result: " + exec );
        return exec;

//        return treeMaker.Exec( treeMaker.App( debugMethod, List.from( jcExpressions ) ) );
    }
    /**
     * Loop to get the JCBinary
     * @param name method name
     * @param params method parameter
     * @return "method(): param[0]=" + param[0] + ",param[1]=" + param[1]......
     */
    private JCTree.JCBinary generateParametersString(Name name, List<JCTree.JCVariableDecl> params ) {
        JCTree.JCBinary ret = null;
        int pos = 0;
        for( int i = 0; i < params.length()*2-1; i ++ ) {
            if( i == 0 ) {
                ret = treeMaker.Binary(JCTree.Tag.PLUS, treeMaker.Literal( name.toString() + "(): " + params.get( 0 ).getName() + "=" ), treeMaker.Ident( params.get( 0 ) ) );
                continue;
            }
            // i is even, then pos = i/2
            pos = (i%2) == 0? i/2: (i+1)/2;
            // i is even, then return Indent ( b ), else Literal( "b" )
            ret = treeMaker.Binary( JCTree.Tag.PLUS, ret, ( (i%2) == 0? treeMaker.Ident( params.get( pos ) ): treeMaker.Literal( "," + params.get( pos ).getName() + "=" ) ) );
        }

        return ret;
    }

//    /**
//     * recursion execution
//     * @return
//     */
//    private JCTree.JCBinary recursionGetting( int pos, List<JCTree.JCVariableDecl> params ) {
//        JCTree.JCVariableDecl right = params.get( pos/2 );
//    }
//
//    /**
//     * Get JCBinary according to input type
//     * @param type: 1: Ident, 0: Literal
//     * @param right: The ident or literal value
//     * @param left: The JCBinary
//     * @return JCBinary
//     */
//    private JCTree.JCBinary getParameterBinary(byte type, JCTree.JCBinary left, JCTree.JCVariableDecl right ) {
//        return treeMaker.Binary(JCTree.Tag.PLUS, left, (type == 1? treeMaker.Ident( right ): treeMaker.Literal( right ) ) );
//    }


    /**
     * Get lombok created "log" field in the class
     * @return “log.debug()", here log is created by Slf4j
     */
    private JCTree.JCFieldAccess getDebugMethod() {
        JCTree.JCIdent ident = treeMaker.Ident(names.fromString("log"));
        JCTree.JCFieldAccess debugMethod = treeMaker.Select(ident, names.fromString("debug"));
        debugMethod.setType( new Type.JCVoidType() );
        return debugMethod;
    }

    /**
     * Print log
     * @param msg
     */
    void showLog( String msg ) {
        show( Diagnostic.Kind.NOTE, msg );
    }

    void showWarn( String msg ) {
        show( Diagnostic.Kind.WARNING, msg );
    }

    void showError( String msg ) {
        show( Diagnostic.Kind.ERROR, msg );
    }

    void show( Diagnostic.Kind type, String msg ) {
        messager.printMessage( type, msg );
    }
}
