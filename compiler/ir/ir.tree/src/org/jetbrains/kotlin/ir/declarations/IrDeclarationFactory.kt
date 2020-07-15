/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

interface IrDeclarationFactory {
    fun createAnonymousInitializer(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrAnonymousInitializerSymbol,
        isStatic: Boolean = false,
    ): IrAnonymousInitializer

    fun createClass(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrClassSymbol,
        name: Name,
        kind: ClassKind,
        visibility: Visibility,
        modality: Modality,
        isCompanion: Boolean = false,
        isInner: Boolean = false,
        isData: Boolean = false,
        isExternal: Boolean = false,
        isInline: Boolean = false,
        isExpect: Boolean = false,
        isFun: Boolean = false,
        source: SourceElement = SourceElement.NO_SOURCE,
    ): IrClass

    fun createConstructor(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrConstructorSymbol,
        name: Name,
        visibility: Visibility,
        returnType: IrType,
        isInline: Boolean,
        isExternal: Boolean,
        isPrimary: Boolean,
        isExpect: Boolean,
    ): IrConstructor

    fun createEnumEntry(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrEnumEntrySymbol,
        name: Name,
    ): IrEnumEntry

    fun createErrorDeclaration(
        startOffset: Int,
        endOffset: Int,
        descriptor: DeclarationDescriptor,
    ): IrErrorDeclaration

    fun createField(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrFieldSymbol,
        name: Name,
        type: IrType,
        visibility: Visibility,
        isFinal: Boolean,
        isExternal: Boolean,
        isStatic: Boolean,
    ): IrField

    fun createFunction(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrSimpleFunctionSymbol,
        name: Name,
        visibility: Visibility,
        modality: Modality,
        returnType: IrType,
        isInline: Boolean,
        isExternal: Boolean,
        isTailrec: Boolean,
        isSuspend: Boolean,
        isOperator: Boolean,
        isExpect: Boolean,
        isFakeOverride: Boolean = origin == IrDeclarationOrigin.FAKE_OVERRIDE,
    ): IrSimpleFunction

    fun createFakeOverrideFunction(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        name: Name,
        visibility: Visibility,
        modality: Modality,
        returnType: IrType,
        isInline: Boolean,
        isExternal: Boolean,
        isTailrec: Boolean,
        isSuspend: Boolean,
        isOperator: Boolean,
        isExpect: Boolean,
    ): IrSimpleFunction

    fun createLocalDelegatedProperty(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrLocalDelegatedPropertySymbol,
        name: Name,
        type: IrType,
        isVar: Boolean,
    ): IrLocalDelegatedProperty

    fun createProperty(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrPropertySymbol,
        name: Name,
        visibility: Visibility,
        modality: Modality,
        isVar: Boolean,
        isConst: Boolean,
        isLateinit: Boolean,
        isDelegated: Boolean,
        isExternal: Boolean,
        isExpect: Boolean = false,
        isFakeOverride: Boolean = origin == IrDeclarationOrigin.FAKE_OVERRIDE,
    ): IrProperty

    fun createFakeOverrideProperty(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        name: Name,
        visibility: Visibility,
        modality: Modality,
        isVar: Boolean,
        isConst: Boolean,
        isLateinit: Boolean,
        isDelegated: Boolean,
        isExternal: Boolean,
        isExpect: Boolean,
    ): IrFakeOverrideProperty

    fun createTypeAlias(
        startOffset: Int,
        endOffset: Int,
        symbol: IrTypeAliasSymbol,
        name: Name,
        visibility: Visibility,
        expandedType: IrType,
        isActual: Boolean,
        origin: IrDeclarationOrigin,
    ): IrTypeAlias

    fun createTypeParameter(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrTypeParameterSymbol,
        name: Name,
        index: Int,
        isReified: Boolean,
        variance: Variance,
    ): IrTypeParameter

    fun createValueParameter(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrValueParameterSymbol,
        name: Name,
        index: Int,
        type: IrType,
        varargElementType: IrType?,
        isCrossinline: Boolean,
        isNoinline: Boolean,
    ): IrValueParameter
}
