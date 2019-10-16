/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.v4.netbeans.v8.project.helper.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.TerminalNode;

import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaBaseListener;

import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.ClassDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.ClassOrInterfaceModifierContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.ClassOrInterfaceTypeContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.ImportDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.PackageDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.QualifiedNameContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.TypeListContext;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.impl.JavaParser.TypeTypeContext;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class SuperClassFinder extends JavaBaseListener {
    protected       String                  currentPackageName;
    protected       String                  simpleClassName;
    protected final HashMap<String, String> classToPackages;
    protected       boolean                 publicType;
    protected final List<String>            fullImportedPackages;
    protected       JavaSourceClass         superClass;
    protected final List<JavaSourceClass>   implementedInterfaces;

    public JavaSourceClass getSuperClass() {
        return superClass;
    }
    
    public JavaSourceClass getJavaClass() {
        return new JavaSourceClass(currentPackageName, simpleClassName);
    }

    public List<JavaSourceClass> getImplementedInterfaces() {
        return implementedInterfaces;
    }
    
    public SuperClassFinder() {
        this.currentPackageName = null; // if there is no package statement then there is no package
        this.simpleClassName = null;
        this.classToPackages = new HashMap<>();
        this.publicType = false; // If there is no public modifier it is a private
        this.fullImportedPackages = new ArrayList<>();
        this.superClass = null;
        this.implementedInterfaces = new ArrayList<>();
    }

    
    @Override
    public void exitPackageDeclaration(PackageDeclarationContext ctx) {
//        System.out.println("SuperClassFinder:exitPackageDeclaration(PackageDeclarationContext) : begin");
        QualifiedNameContext qnc = ctx.qualifiedName();
        if (qnc != null) {
            currentPackageName = qnc.getText();
//            System.out.println("- package name=" + currentPackageName);
        }
//        System.out.println("SuperClassFinder:exitPackageDeclaration(PackageDeclarationContext) : end");
    }
    

    private static final Pattern PATTERN = Pattern.compile("\\p{Lu}");
    @Override
    public void exitImportDeclaration(ImportDeclarationContext ctx) {
//        System.out.println("SuperClassFinder:exitImportDeclaration(ImportDeclarationContext) : begin");
        QualifiedNameContext qnc = ctx.qualifiedName();
        String qualifiedName = qnc.getText();
        TerminalNode starTN = ctx.STAR();
        if (starTN == null) {
         // We look for the first occurrence of an uppercase character
         // It marks the begin of the class name.
            Matcher matcher = PATTERN.matcher(qualifiedName);
            if (matcher.find()) {
                int index = matcher.start();
             // Normally, index is always greater than or equal to 2
             // but in case of erroneous import statement, it is possible to have
             // index equalt to 0 or 1. In such a case, we ignore the import
             // statement
                if (index >= 2) {
                    String packageName = qualifiedName.substring(0, index - 1);
                    String className = qualifiedName.substring(index);
//                    System.out.println("- imported class package name=" + packageName);
//                    System.out.println("  imported class name=" + className);
                    classToPackages.put(className, packageName);
                }
            } // If matcher finds nothing: import statement is erroneous
        } else {
         // There is a star so qualified name represents a package name
            String packageName = qualifiedName;
            fullImportedPackages.add(packageName);
        }
//        System.out.println("SuperClassFinder:exitImportDeclaration(ImportDeclarationContext) : end");
    }
    

    @Override
    public void exitClassOrInterfaceModifier(ClassOrInterfaceModifierContext ctx) {
//        System.out.println("SuperClassFinder:exitClassOrInterfaceModifier(ClassOrInterfaceModifierContext) : begin");
        TerminalNode publicTN = ctx.PUBLIC();
        publicType = publicTN != null;
//        System.out.println("- is it a public type? " + publicType);
//        System.out.println("SuperClassFinder:exitClassOrInterfaceModifier(ClassOrInterfaceModifierContext) : end");
    }
    

    @Override
    public void exitClassDeclaration(ClassDeclarationContext ctx) {
//        System.out.println("SuperClassFinder:exitClassDeclaration(ClassDeclarationContext) : begin");
        TerminalNode classTN = ctx.CLASS();
     // We look for a class
        if (classTN != null) {
            simpleClassName = ctx.IDENTIFIER().getSymbol().getText();
//            System.out.println("- parsed type is a class!");
//            System.out.println("  and its name is :" + simpleClassName);

         // We recover the eventual inherited class name (qualified or not)
            TypeTypeContext ttc = ctx.typeType();
            if (ttc != null) {
//                System.out.println("- parsed type has a superclass!");
                ClassOrInterfaceTypeContext coitc = ttc.classOrInterfaceType();
                List<TerminalNode> classIdentifiers = coitc.IDENTIFIER();
             // We build the potentially qualified inherited class name
                StringBuilder className = new StringBuilder();
                Iterator<TerminalNode> cIIt = classIdentifiers.iterator();
                TerminalNode classIdentifierTN;
                while (cIIt.hasNext()) {
                    classIdentifierTN = cIIt.next();
                    if (!className.toString().equals(""))
                        className.append(".");
                    className.append(classIdentifierTN.getSymbol().getText());
                }
             // We determine if the recovered name of inherited class is qualified
             // or not
                String qualifiedClassName = className.toString();
//                System.out.println("  qualified superclass name=" + qualifiedClassName);
                String superClassPackageName, superClassName;
                int index = qualifiedClassName.lastIndexOf(".");
                if (index == -1) {
                 // No dot has been found so it means that our class name is not
                 // qualified
                    superClassName = qualifiedClassName;
                 // The package of the super class must be defined in an import
                 // clauses or it is in the same package as current class
                    superClassPackageName = classToPackages.get(superClassName);
                    if (superClassPackageName == null) {
                     // Super class is not defined in a non stared import statement
                     // Currently we do not take into account stared imports
                       
                     // So the last possibility is that the package of super class
                     // is the package of the current class
                        superClassPackageName = currentPackageName;
                    }
                } else {
                 // We have found a dot so it means our class name is qualified
                    superClassPackageName =
                                         qualifiedClassName.substring(0, index);
                    superClassName = qualifiedClassName.substring(index + 1);
                }
                superClass = new JavaSourceClass(superClassPackageName,
                                                 superClassName       );
            }
            
         // Now we manage the interface implementations
            TypeListContext tlc = ctx.typeList();
            if (tlc != null) {
                List<TypeTypeContext> typeTypes = tlc.typeType();
                Iterator<TypeTypeContext> ttcIt = typeTypes.iterator();
                while (ttcIt.hasNext()) {
                    ttc = ttcIt.next();
                    if (ttc != null) {
//                        System.out.println("- parsed type implements an interface!");
                        ClassOrInterfaceTypeContext coitc = ttc.classOrInterfaceType();
                        List<TerminalNode> classIdentifiers = coitc.IDENTIFIER();
                     // We build the potentially qualified implemented interface name
                        StringBuilder qualifiedClassNameBuilder = new StringBuilder();
                        Iterator<TerminalNode> cIIt = classIdentifiers.iterator();
                        TerminalNode classIdentifierTN;
                        while (cIIt.hasNext()) {
                            classIdentifierTN = cIIt.next();
                            if (!qualifiedClassNameBuilder.toString().equals(""))
                                qualifiedClassNameBuilder.append(".");
                            qualifiedClassNameBuilder.append(classIdentifierTN.getSymbol().getText());
                        }
                        String qualifiedClassName = qualifiedClassNameBuilder.toString();
//                        System.out.println("  its name is " + qualifiedClassName);
                     // We extract its package name
                        Matcher matcher = PATTERN.matcher(qualifiedClassName);
                        if (matcher.find()) {
                            int index = matcher.start();
                            String packageName;
                            String className;
                         // Only two cases must be managed:
                         // - index == 0 : no package specified,
                         // - index >= 2 : a package is specified
                         // if index == 1 then the implements clause is erroneous
                         // so we skip it.
                            if (index == 0) {
                             // class name does not contain a package name
                                className = qualifiedClassName;
                             // package name is not defined in the implements
                             // clause. So we look for an import statement
                             // for that class
                                packageName = classToPackages.get(className);
                             // If our interface is not imported then it must be
                             // in current package
                                JavaSourceClass jsc;
                                if (packageName == null) {
                                 // Current class may have or may not have a
                                 // package!
                                 // It is not forbidden even if not recommended
                                 // So currentPackageName may be null but it is
                                 // allowed by JavaSourceClass
                                    packageName = currentPackageName;
                                }
                                jsc = new JavaSourceClass
                                                (packageName,
                                                 className  );
//                                System.out.println("- implementyed class package name=" + packageName);
//                                System.out.println("  implemented class name=" + className);
                                implementedInterfaces.add(jsc);
                            } else if (index >= 2) {
                                packageName = qualifiedClassName.substring(0, index - 1);
                                className = qualifiedClassName.substring(index);
//                                System.out.println("- implementyed class package name=" + packageName);
//                                System.out.println("  implemented class name=" + className);
                                JavaSourceClass jsc = new JavaSourceClass
                                                (packageName,
                                                 className  );
                                implementedInterfaces.add(jsc);
                            }
                        }
                    }
                }
            }
        }
//        System.out.println("SuperClassFinder:exitClassDeclaration(ClassDeclarationContext) : end");
    }
}