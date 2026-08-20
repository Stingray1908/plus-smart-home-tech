
# compose
docker-compose -f commerce/docker-compose.yml up --build    

docker run -d --name shopping_store_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=shop_db -p 5432:5432 --restart unless-stopped postgres:15-alpine

docker run -d --name shopping_cart_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=cart_db -p 5433:5432 --restart unless-stopped postgres:15-alpine

docker run -d --name warehouse_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=warehouse_db -p 5434:5432 --restart unless-stopped postgres:15-alpine

docker run -d --name order_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=order_db -p 5435:5432 --restart unless-stopped postgres:15-alpine

docker run -d --name payment_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=payment_db -p 5436:5432 --restart unless-stopped postgres:15-alpine

docker run -d --name delivery_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=delivery_db -p 5437:5432 --restart unless-stopped postgres:15-alpine

/
