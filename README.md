
# compose
docker-compose -f commerce/docker-compose.yml up --build    


# db shopping_store_db
docker run -d --name shopping_store_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=smart_home_db -p 5432:5432 --restart unless-stopped postgres:15-alpine

# db shopping_cart_db
docker run -d --name shopping_cart_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=cart_db -p 5433:5432 --restart unless-stopped postgres:15-alpine

# db warehouse
docker run -d --name warehouse_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=warehouse_db -p 5434:5432 --restart unless-stopped postgres:15-alpine

провал
assertions │                 26 │                21 │
assertions │                 26 │                15 │
assertions │                 26 │                00 │
assertions │                 26 │                00 │
assertions │                 26 │                00 │
