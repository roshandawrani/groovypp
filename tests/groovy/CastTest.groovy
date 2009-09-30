package groovy

class CastTest extends GroovyShellTestCase {
    
    void testCast() {
      shell.evaluate("""
        @Compile
        def methodWithShort(Short) {
          return "short"
        }

        @Compile
        def u() {
          def s = (Short) 5
          methodWithShort(s)
        }
        u();
      """
      )
    }

    void testIntCast() {
      shell.evaluate("""
        @Compile
        def u() {
          def i = (Integer) 'x'
          assert i instanceof Integer
        }
        u();
      """
      )
    }
    
    void testCharCompare() {
      shell.evaluate("""
        @Compile
        def u() {
          def i = (Integer) 'x'
          def c = 'x'

          assert i == c
          assert i =='x'
          assert c == 'x'
          assert i == i
          assert c == c

          assert 'x' == 'x'
          assert 'x' == c
          assert 'x' == i
        }
        u();
      """
      )

    }
    
    void testCharCast() {
      shell.evaluate("""
        @Compile
        def u() {
          def c = (Character) 'x'
          assert c instanceof Character
          c = (Character)10
          assert c instanceof Character
        }
        u();
      """
      )
    }

    void testPrimitiveCasting() {
      shell.evaluate("""
        @Compile
        def u() {
          def d = 1.23
          def i1 = (int)d
          def i2 = (Integer)d
          assert i1.class.name == 'java.lang.Integer'
          assert i2.class.name == 'java.lang.Integer'

          def ch = (char) i1
          assert ch.class.name == 'java.lang.Character'

          def dd = (double)d
          assert dd.class.name == 'java.lang.Double'
        }
        u();
      """
      )
    }

    void testAsSet() {
      shell.evaluate("""
        @Compile
        def u() {
          def mySet = [2, 3, 4, 3] as SortedSet
          assert mySet instanceof SortedSet

          // identity test
          mySet = {} as SortedSet
          assert mySet.is ( mySet as SortedSet )

          mySet = [2, 3, 4, 3] as Set
          assert mySet instanceof HashSet

          // identitiy test
          mySet = {} as Set
          assert mySet.is ( mySet as Set )

          // array test
          mySet = new String[2] as Set // Array of 2 null Strings
          assert mySet instanceof Set
          assert mySet.size() == 1
          assert mySet.iterator().next() == null

          mySet = "a,b".split(",") as Set // Array of 2 different Strings
          assert mySet instanceof Set
          assert mySet.size() == 2
          assert mySet == new HashSet([ "a", "b" ])

          mySet = "a,a".split(",") as Set // Array of 2 different Strings
          assert mySet instanceof Set
          assert mySet.size() == 1
          assert mySet == new HashSet([ "a" ])
        }
        u();
      """
      )
    }

    void testCastToAbstractClass() {
      shell.evaluate("""
        @Compile
        def u() {
          def closure = { 42 }
          def myList = closure as AbstractList
          assert myList[-1] == 42
          assert myList.size() == 42
        }
        u();
      """
      )
    }
}
