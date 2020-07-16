/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.jvm.checkers

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.load.kotlin.computeJvmDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils.*
import org.jetbrains.kotlin.resolve.LanguageVersionSettingsProvider
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyPrivateApi
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.annotations.*
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.util.getNonPrivateTraitMembersForDelegation
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

class JvmDefaultChecker(val jvmTarget: JvmTarget, project: Project) : DeclarationChecker {

    private val ideService = LanguageVersionSettingsProvider.getInstance(project)

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        val jvmDefaultMode = context.languageVersionSettings.getFlag(JvmAnalysisFlags.jvmDefaultMode)

        descriptor.annotations.findAnnotation(JVM_DEFAULT_FQ_NAME)?.let { annotationDescriptor ->
            val reportOn = DescriptorToSourceUtils.getSourceFromAnnotation(annotationDescriptor) ?: declaration
            if (!isInterface(descriptor.containingDeclaration)) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_NOT_IN_INTERFACE.on(reportOn))
            } else if (jvmTarget == JvmTarget.JVM_1_6) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_IN_JVM6_TARGET.on(reportOn, "JvmDefault"))
            } else if (!jvmDefaultMode.isEnabled) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_IN_DECLARATION.on(declaration, "JvmDefault"))
            }
            return@check
        }

        descriptor.annotations.findAnnotation(JVM_DEFAULT_NO_COMPATIBILITY_FQ_NAME)?.let { annotationDescriptor ->
            val reportOn = DescriptorToSourceUtils.getSourceFromAnnotation(annotationDescriptor) ?: declaration
            if (jvmTarget == JvmTarget.JVM_1_6) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_IN_JVM6_TARGET.on(reportOn, "JvmDefaultWithoutCompatibility"))
            } else if (!jvmDefaultMode.isEnabled) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_IN_DECLARATION.on(reportOn, "JvmDefaultWithoutCompatibility"))
            }
            return@check
        }

        if (descriptor is ClassDescriptor) {
            val hasDeclaredJvmDefaults =
                descriptor.unsubstitutedMemberScope.getContributedDescriptors().filterIsInstance<CallableMemberDescriptor>().any {
                    it.kind.isReal && it.isCompiledToJvmDefault(jvmDefaultMode)
                }
            if (!hasDeclaredJvmDefaults && !checkJvmDefaultsInHierarchy(descriptor, jvmDefaultMode)) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_THROUGH_INHERITANCE.on(declaration))
            }
        }


        if (isInterface(descriptor.containingDeclaration)) {
            val memberDescriptor = descriptor as? CallableMemberDescriptor ?: return
            if (descriptor is PropertyAccessorDescriptor) return

            if (memberDescriptor.overriddenDescriptors.any { it.annotations.hasAnnotation(JVM_DEFAULT_FQ_NAME) }) {
                context.trace.report(ErrorsJvm.JVM_DEFAULT_REQUIRED_FOR_OVERRIDE.on(declaration))
            } else if (jvmDefaultMode.isEnabled) {
                descriptor.overriddenDescriptors.flatMap { OverridingUtil.getOverriddenDeclarations(it) }.toSet().let {
                    for (realDescriptor in OverridingUtil.filterOutOverridden(it)) {
                        if (realDescriptor is JavaMethodDescriptor && realDescriptor.modality != Modality.ABSTRACT) {
                            return context.trace.report(ErrorsJvm.NON_JVM_DEFAULT_OVERRIDES_JAVA_DEFAULT.on(declaration))
                        }
                    }
                }
            }
        } else if (jvmDefaultMode.isEnabled &&
            !isInterface(descriptor) &&
            !isAnnotationClass(descriptor) &&
            descriptor is ClassDescriptor
        ) {
            val performSpecializationCheck = jvmDefaultMode.isCompatibility && !descriptor.hasJvmDefaultNoCompatibilityAnnotation() &&
                    //TODO: maybe remove this check for JVM compatibility
                    !(descriptor.modality !== Modality.OPEN && descriptor.modality !== Modality.ABSTRACT || descriptor.isEffectivelyPrivateApi)

            val performClashCheck = descriptor.getSuperClassNotAny() != null

            if (performClashCheck || performSpecializationCheck) {
                getNonPrivateTraitMembersForDelegation(
                    descriptor,
                    returnImplNotDelegate = true
                ).forEach { (inheritedMember, actualImplementation) ->
                    if (actualImplementation.isCompiledToJvmDefaultWithProperMode(jvmDefaultMode)) {
                        if (actualImplementation is FunctionDescriptor && inheritedMember is FunctionDescriptor) {
                            if (checkSpecializationInCompatibilityMode(
                                    inheritedMember,
                                    actualImplementation,
                                    context,
                                    declaration,
                                    performSpecializationCheck
                                )
                            ) {
                                checkPossibleClashMember(inheritedMember, jvmDefaultMode, context, declaration)
                            }
                        } else if (actualImplementation is PropertyDescriptor && inheritedMember is PropertyDescriptor) {
                            val getterImpl = actualImplementation.getter
                            val getterInherited = inheritedMember.getter
                            if (getterImpl == null || getterInherited == null || !jvmDefaultMode.isCompatibility ||
                                checkSpecializationInCompatibilityMode(getterImpl, getterImpl, context, declaration, performSpecializationCheck)
                            ) {
                                if (actualImplementation.isVar && inheritedMember.isVar) {
                                    val setterImpl = actualImplementation.setter
                                    val setterInherited = inheritedMember.setter
                                    if (setterImpl != null && setterInherited != null) {
                                        if (!checkSpecializationInCompatibilityMode(
                                                setterImpl,
                                                setterImpl,
                                                context,
                                                declaration,
                                                performSpecializationCheck
                                            )
                                        ) return@forEach
                                    }
                                }

                                checkPossibleClashMember(inheritedMember, jvmDefaultMode, context, declaration)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkSpecializationInCompatibilityMode(
        inheritedFun: FunctionDescriptor,
        actualImplementation: FunctionDescriptor,
        context: DeclarationCheckerContext,
        declaration: KtDeclaration,
        performSpecializationCheck: Boolean
    ): Boolean {
        if (!performSpecializationCheck) return true
        val inheritedSignature = inheritedFun.computeJvmDescriptor(withReturnType = true, withName = false)
        val originalImplementation = actualImplementation.original
        val actualSignature = originalImplementation.computeJvmDescriptor(withReturnType = true, withName = false)
        if (inheritedSignature != actualSignature) {
            //NB: this diagnostics should be a bit tuned, see box/jvm8/defaults/allCompatibility/kt14243_2.kt for details
            context.trace.report(
                ErrorsJvm.EXPLICIT_OVERRIDE_REQUIRED_IN_COMPATIBILITY_MODE.on(
                    declaration,
                    getDirectMember(inheritedFun),
                    getDirectMember(originalImplementation)
                )
            )
            return false
        }
        return true
    }

    private fun checkPossibleClashMember(
        inheritedFun: CallableMemberDescriptor,
        jvmDefaultMode: JvmDefaultMode,
        context: DeclarationCheckerContext,
        declaration: KtDeclaration
    ) {
        val clashMember = findPossibleClashMember(inheritedFun, jvmDefaultMode)
        if (clashMember != null) {
            context.trace.report(
                ErrorsJvm.EXPLICIT_OVERRIDE_REQUIRED_IN_MIXED_MODE.on(
                    declaration,
                    getDirectMember(inheritedFun),
                    getDirectMember(clashMember)
                )
            )
        }
    }

    private fun findPossibleClashMember(
        inheritedFun: CallableMemberDescriptor,
        jvmDefaultMode: JvmDefaultMode
    ): CallableMemberDescriptor? {
        val classDescriptor = inheritedFun.containingDeclaration
        if (classDescriptor !is ClassDescriptor || classDescriptor.getSuperClassNotAny() == null) return null
        val classMembers =
            inheritedFun.overriddenDescriptors.filter { !isInterface(it.containingDeclaration) && !isAnnotationClass(it.containingDeclaration) }
        val implicitDefaultImplsDelegate =
            classMembers.firstOrNull { getNonPrivateTraitMembersForDelegation(it, true)?.isCompiledToJvmDefaultWithProperMode(jvmDefaultMode) == false }
        if (implicitDefaultImplsDelegate != null) return implicitDefaultImplsDelegate
        return classMembers.firstNotNullResult { findPossibleClashMember(it, jvmDefaultMode) }
    }

    private fun checkJvmDefaultsInHierarchy(descriptor: DeclarationDescriptor, jvmDefaultMode: JvmDefaultMode): Boolean {
        if (jvmDefaultMode.isEnabled) return true

        if (descriptor !is ClassDescriptor) return true

        return descriptor.unsubstitutedMemberScope.getContributedDescriptors().filterIsInstance<CallableMemberDescriptor>()
            .all { memberDescriptor ->
                memberDescriptor.kind.isReal || OverridingUtil.filterOutOverridden(memberDescriptor.overriddenDescriptors.toSet()).all {
                    !isInterface(it.containingDeclaration) || !it.isCompiledToJvmDefaultWithProperMode(jvmDefaultMode) || it.modality == Modality.ABSTRACT
                }
            }
    }

    private fun CallableMemberDescriptor.isCompiledToJvmDefaultWithProperMode(compilationDefaultMode: JvmDefaultMode): Boolean {
        val jvmDefault =
            if (this is DeserializedDescriptor) compilationDefaultMode else ideService?.getModuleLanguageVersionSettings(module)
                ?.getFlag(JvmAnalysisFlags.jvmDefaultMode) ?: compilationDefaultMode
        return isCompiledToJvmDefault(jvmDefault)
    }

}