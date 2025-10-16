open class Human (addName: String, addSurname: String): movable {
    override var name: String = addName
    override var surname: String = addSurname
    override var age: Int = -1
    override var speed: Int = -1
    override var x = 0
    override var y = 0

    constructor(addName: String, addSurname: String, _age: Int, _speed: Int): this(addName, addSurname){
        age = _age
        speed = _speed
        x = 0
        y = 0
    }

    override fun move(_toX: Int, _toY: Int){
        x += _toX
        y += _toY
    }
    override fun prr(){
        print("$name $surname speed:$speed x:$x y:$y\n")
    }

    //something
}