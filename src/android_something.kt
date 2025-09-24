public open class Human {
    var name: Char = 'A'
    var surname: Char = 'A'
    var age: Int = -1
    var speed: Int = -1
    var x = 0
    var y = 0

    constructor(_name: Char, _surname: Char, _age: Int, _speed: Int){
        name = _name
        surname = _surname
        age = _age
        speed = _speed
        x = 0
        y = 0
    }

    fun move(_toX: Int, _toY: Int){
        x += _toX
        y += _toY
    }
    fun prr(){
        print("$name $surname speed:$speed x:$x y:$y\n")
    }
}

fun main() {

    val people: Array<Human?> = Array(17) { null }

    for(i in 0..16){
        people[i] = Human(('A'..'Z').random(), ('A'..'Z').random(), (1..10).random(), (1..10).random())
    }

    for(j in 0..10){
        for(i in 0..16){
            val cur = people[i]!! //утв не null
            cur.move(cur.speed*listOf(-1,1).random(), cur.speed*listOf(-1,1).random())
        }
    }

    for(i in 0..16){
        val cur = people[i]!!
        cur.prr()
    }
}