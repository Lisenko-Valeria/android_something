import kotlin.collections.listOf
import kotlin.random.Random

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

class Driver: Human {

    constructor(_name: Char, _surname: Char, _age: Int, _speed: Int) :
            super(_name, _surname, _age, _speed)

    fun move( _direction : Char, _distance: Int) {
        if ( _direction == 'x') x += _distance
        else y += _distance
    }
}

fun main() {

    val people: Array<Human> = Array(4) {Human('A', 'A', 0, 0)}

    for(i in 0..2){
        people[i] = Human(('A'..'Z').random(), ('A'..'Z').random(), (1..10).random(), (1..4).random())
        people[i].x = 10;
        people[i].y = 10;
    }

    people[3] = Driver(('A'..'Z').random(), ('A'..'Z').random(), (1..10).random(), (1..4).random())
    people[3].x = 10;
    people[3].y = 10;

    val plane = Array(40) { Array(40) { Array(4) { "" } } }

    for (i in 0..3) {
        plane[10][10][i] = "${people[i].name} ${people[i].surname}"
    }

    var tempx : Int = 0
    var tempy : Int = 0

    for(j in 0..3){
        for(i in 0..3){
            val cur = people[i] //утв не null

            tempx = people[i].x
            tempy = people[i].y

            if (cur is Driver) {
                val direction = if (Random.nextBoolean()) 'x' else 'y'
                cur.move(direction, cur.speed * listOf(-1, 1).random())
            }
            else
                cur.move(cur.speed*listOf(-1,1).random(), cur.speed*listOf(-1,1).random())

            plane[tempy][tempx][i] = ""
            plane[cur.y][cur.x][i] = "${cur.name} ${cur.surname}"

            println("${cur.name} ${cur.surname} переместился из ($tempx, $tempy) в (${cur.x}, ${cur.y})")
            println()
        }
    }
    for (y in 0 until 40) {
        for (x in 0 until 40) {
            val occupants = plane[y][x].filter { it.isNotEmpty() }
            if (occupants.isNotEmpty()) {
                print("($x,$y): ${occupants.joinToString(", ")} | ")
            }
        }

    }
}