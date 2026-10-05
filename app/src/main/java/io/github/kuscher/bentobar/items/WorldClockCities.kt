package io.github.kuscher.bentobar.items

/**
 * Large cities that are no time zone's name, each with the zone it is in, so that World clock's
 * search finds "Munich" (Europe/Berlin) and "San Francisco" (America/Los_Angeles) and adds them under
 * those names. Pure data: nothing is looked up anywhere. A city a zone is called by anyway (Tokyo,
 * Berlin) needs no entry, and `WorldClockTest` keeps such entries out; one whose zone a device doesn't
 * know is not offered there.
 */
object WorldClockCities {
    data class City(val name: String, val zone: String)

    /** One line a zone: its id, then the cities in it. */
    private const val TABLE = """
America/Los_Angeles=San Francisco, San Jose, San Diego, Oakland, Sacramento, Seattle, Portland, Las Vegas
America/Denver=Salt Lake City, Albuquerque
America/Chicago=Dallas, Houston, Austin, San Antonio, Minneapolis, St. Louis, Kansas City, New Orleans, Nashville, Milwaukee
America/New_York=Boston, Washington DC, Philadelphia, Miami, Atlanta, Pittsburgh, Charlotte, Orlando, Tampa, Baltimore, Cleveland, Columbus, Cincinnati, Raleigh
America/Toronto=Ottawa, Quebec City
America/Edmonton=Calgary
America/Mexico_City=Guadalajara
America/Puerto_Rico=San Juan
America/Jamaica=Kingston
America/Panama=Panama City
America/Guatemala=Guatemala City
America/El_Salvador=San Salvador
America/Sao_Paulo=Rio de Janeiro, Brasilia, Belo Horizonte, Curitiba, Porto Alegre
America/Bahia=Salvador
America/Bogota=Medellin, Cali
America/Guayaquil=Quito
Europe/Berlin=Munich, Frankfurt, Hamburg, Cologne, Stuttgart, Dusseldorf, Dresden, Leipzig, Nuremberg, Hanover
Europe/Zurich=Geneva, Basel, Bern
Europe/Vienna=Salzburg, Innsbruck
Europe/Madrid=Barcelona, Valencia, Seville, Bilbao, Malaga
Europe/Rome=Milan, Naples, Turin, Florence, Venice, Bologna
Europe/Paris=Lyon, Marseille, Toulouse, Nice, Bordeaux, Strasbourg
Europe/London=Manchester, Birmingham, Edinburgh, Glasgow, Liverpool, Leeds, Bristol, Cardiff, Oxford
Europe/Dublin=Cork
Europe/Amsterdam=Rotterdam, The Hague
Europe/Brussels=Antwerp
Europe/Lisbon=Porto
Europe/Warsaw=Krakow
Europe/Stockholm=Gothenburg
Europe/Oslo=Bergen
Europe/Moscow=St. Petersburg
Europe/Athens=Thessaloniki
Europe/Istanbul=Ankara, Izmir
Europe/Kiev=Odesa, Kharkiv, Lviv
Asia/Dubai=Abu Dhabi
Asia/Qatar=Doha
Asia/Riyadh=Jeddah, Mecca
Asia/Kuwait=Kuwait City
Asia/Bahrain=Manama
Asia/Jerusalem=Haifa
Africa/Cairo=Alexandria
Africa/Casablanca=Rabat, Marrakesh
Africa/Lagos=Abuja
Africa/Johannesburg=Cape Town, Durban, Pretoria
Asia/Kolkata=Mumbai, Delhi, New Delhi, Bengaluru, Bangalore, Chennai, Hyderabad, Pune, Ahmedabad, Jaipur
Asia/Karachi=Islamabad, Lahore
Asia/Shanghai=Beijing, Shenzhen, Guangzhou, Chengdu, Hangzhou, Wuhan, Nanjing, Tianjin
Asia/Tokyo=Osaka, Kyoto, Yokohama, Nagoya, Sapporo, Fukuoka
Asia/Seoul=Busan
Asia/Ho_Chi_Minh=Hanoi
Asia/Jakarta=Surabaya
Asia/Makassar=Bali, Denpasar
Asia/Manila=Cebu
Asia/Bangkok=Phuket, Chiang Mai
Asia/Kuala_Lumpur=Penang
Asia/Almaty=Astana
Pacific/Auckland=Wellington, Christchurch
Australia/Brisbane=Gold Coast
Pacific/Fiji=Suva
Pacific/Tahiti=Papeete
"""

    val all: List<City> by lazy {
        TABLE.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.flatMap { line ->
            val zone = line.substringBefore('=')
            line.substringAfter('=').split(',').asSequence().map { City(it.trim(), zone) }
        }.toList()
    }
}
