class Driver: Human {

    constructor(_name: String, _surname: String, _age: Int, _speed: Int) :
            super(_name, _surname, _age, _speed)

    fun move( _direction : Char, _distance: Int) {
        if ( _direction == 'x') x += _distance
        else y += _distance
    }
}