open class Human: movable {
    override var name: Char = 'A'
    override var surname: Char = 'A'
    override var age: Int = -1
    override var speed: Int = -1
    override var x = 0
    override var y = 0

    constructor(_name: Char, _surname: Char, _age: Int, _speed: Int){
        name = _name
        surname = _surname
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
}