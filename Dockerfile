FROM nunopreguica/sd2526tpbase

# working directory inside docker image
WORKDIR /home/sd

ENV CLIENT_ID=your_zoho_client_id
ENV CLIENT_SECRET=your_zoho_client_secret
ENV REFRESH_TOKEN=your_zoho_refresh_token

ADD hibernate.cfg.xml .
ADD messages.props .

COPY tls/*.ks /home/sd/

# copy the jar created by assembly to the docker image
COPY target/sd*.jar sd2526.jar