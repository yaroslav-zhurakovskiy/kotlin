interface IBase<T> {
    fun foo() {}
    fun bar() {}
}

typealias AliasedIBase1 = IBase<String>
typealias AliasedIBase = AliasedIBase1

interface IDerived<T> : IBase<T> {
    override fun foo() {}
    fun qux() {}
}

class Test : IDerived<String>, IBase<String> {
    fun test() {
        <!QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE!>super<IBase><!>.foo()
        <!QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE!>super<IBase><!>.bar()
        super<IDerived>.foo()
        super<IDerived>.bar()
        super<IDerived>.qux()
    }
}

class Test2 : IDerived<String>, AliasedIBase {
    fun test() {
        <!QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE!>super<IBase><!>.foo()
        <!QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE!>super<IBase><!>.bar()
        <!QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE!>super<AliasedIBase><!>.foo()
        <!QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE!>super<AliasedIBase><!>.bar()
        super<IDerived>.foo()
        super<IDerived>.bar()
        super<IDerived>.qux()
    }
}