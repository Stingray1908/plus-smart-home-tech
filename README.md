docker-compose -f commerce/docker-compose.yml up --build    


docker run -d --name shopping_store_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=smart_home_db -p 5432:5432 --restart unless-stopped postgres:15-alpine


    hibernate:
      ddl-auto: update 

проверить в yml и docker-compose

