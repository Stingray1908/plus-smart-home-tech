
# compose
docker-compose -f commerce/docker-compose.yml up --build    


# db shopping_store_db
docker run -d --name shopping_store_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=smart_home_db -p 5432:5432 --restart unless-stopped postgres:15-alpine

# db shopping_cart_db
docker run -d --name shopping_cart_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=cart_db -p 5433:5432 --restart unless-stopped postgres:15-alpine


логика
PUT
/api/v1/shopping-cart

приходит это
private final UUID shoppingCartId;
private final Map<UUID, Long> products;

если имя пустое , ошибка
если телеги  нет, ошибка
добавляем в телегу мапу

