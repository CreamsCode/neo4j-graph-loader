provider "aws" {
  region = "us-east-1"
}

variable "hazelcast_ip" {
  description = "IP of Hazelcast Server"
  type        = string
}

resource "aws_vpc" "neo4j_vpc" {
  cidr_block = "10.0.0.0/16"
  tags = {
    Name = "neoVPC"
  }
}

resource "aws_subnet" "neo4j_subnet" {
  vpc_id                  = aws_vpc.neo4j_vpc.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "us-east-1a"
  tags = {
    Name = "Neo4JSubnet"
  }
}

resource "aws_internet_gateway" "neo4j_igw" {
  vpc_id = aws_vpc.neo4j_vpc.id
  tags = {
    Name = "Neo4JInternetGateway"
  }
}

resource "aws_route_table" "neo4j_route_table" {
  vpc_id = aws_vpc.neo4j_vpc.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.neo4j_igw.id
  }
  tags = {
    Name = "Neo4JRouteTable"
  }
}

resource "aws_route_table_association" "neo4j_subnet_association" {
  subnet_id      = aws_subnet.neo4j_subnet.id
  route_table_id = aws_route_table.neo4j_route_table.id
}

resource "aws_security_group" "neo4j_sg" {
  name        = "neo4j-sg"
  vpc_id      = aws_vpc.neo4j_vpc.id
  description = "Allow traffic for Neo4J"

  ingress {
    from_port   = 7474
    to_port     = 7474
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 7687
    to_port     = 7687
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH Access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Neo4JSecurityGroup"
  }
}

resource "aws_instance" "neo4j_server" {
  ami           = "ami-05576a079321f21f8"
  instance_type = "t2.micro"
  subnet_id     = aws_subnet.neo4j_subnet.id
  key_name      = "vockey"
  security_groups = [aws_security_group.neo4j_sg.id]
  iam_instance_profile   = "EMR_EC2_DefaultRole"

  user_data = <<-EOF
    #!/bin/bash
    sudo yum update -y
    sudo yum install -y wget

    sudo rpm --import https://debian.neo4j.com/neotechnology.gpg.key
    sudo bash -c 'echo -e "[neo4j]\nname=Neo4j Yum Repository\nbaseurl=https://yum.neo4j.com/stable\nenabled=1\ngpgcheck=1" > /etc/yum.repos.d/neo4j.repo'
    sudo yum install -y neo4j

    sudo sed -i 's/#dbms.default_listen_address=0.0.0.0/dbms.default_listen_address=0.0.0.0/' /etc/neo4j/neo4j.conf
    sudo sed -i 's/#dbms.default_advertised_address=localhost/dbms.default_advertised_address=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)/' /etc/neo4j/neo4j.conf
    echo "dbms.security.auth_enabled=false" | sudo tee -a /etc/neo4j/neo4j.conf

    sudo systemctl enable neo4j
    sudo systemctl restart neo4j
    echo "Neo4j configurado con autenticación deshabilitada."
  EOF

  tags = {
    Name = "Neo4J-Server"
  }
}

resource "aws_instance" "graph_loader" {
  ami           = "ami-05576a079321f21f8"
  instance_type = "t2.micro"
  subnet_id     = aws_subnet.neo4j_subnet.id
  key_name      = "vockey"
  security_groups = [aws_security_group.neo4j_sg.id]
  iam_instance_profile   = "EMR_EC2_DefaultRole"

  user_data = <<-EOF
    #!/bin/bash
    sudo yum update -y
    sudo yum install -y git wget java-17-amazon-corretto

    wget https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
    sudo tar -xvzf apache-maven-3.9.9-bin.tar.gz -C /opt
    sudo mv /opt/apache-maven-3.9.9 /opt/maven
    echo "export M2_HOME=/opt/maven" | sudo tee /etc/profile.d/maven.sh
    echo "export PATH=\$M2_HOME/bin:\$PATH" | sudo tee -a /etc/profile.d/maven.sh
    source /etc/profile.d/maven.sh

    git clone https://github.com/CreamsCode/neo4j-graph-loader /home/ec2-user/graph-loader
    cd /home/ec2-user/graph-loader

    export HAZELCAST_IP="${var.hazelcast_ip}"
    export NEO4J_USER=$"neo4j"
    export NEO4J_PASSWORD=$"neo4j"
    export NEO4J_IP="${aws_instance.neo4j_server.public_ip}"
    export NEO4J_URI="bolt://$NEO4J_IP:7687"

    echo "HAZELCAST_IP: $HAZELCAST_IP"
    echo "NEO4J_USER: $NEO4J_USER"
    echo "NEO4J_PASSWORD: $NEO4J_PASSWORD"
    echo "NEO4J_IP: $NEO4J_IP"
    echo "NEO4J_URI: $NEO4J_URI"


    sudo /opt/maven/bin/mvn clean install
    sudo java -DHAZELCAST_IP=$HAZELCAST_IP -DNEO4J_USER=$NEO4J_USER -DNEO4J_PASSWORD=$NEO4J_PASSWORD -DNEO4J_URI=$NEO4J_URI -jar Loader/target/Loader-1.0.jar

    echo "GraphLoader instance ready."

  EOF

  tags = {
    Name = "Graph Loader"
  }
}

resource "aws_security_group" "api_sg" {
  name        = "api-sg"
  vpc_id      = aws_vpc.neo4j_vpc.id
  description = "Allow traffic for API"

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH Access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "ApiSecurityGroup"
  }
}

resource "aws_instance" "api_server" {
  ami           = "ami-05576a079321f21f8"
  instance_type = "t2.micro"
  subnet_id     = aws_subnet.neo4j_subnet.id
  key_name      = "vockey"
  security_groups = [aws_security_group.api_sg.id]
  iam_instance_profile = "EMR_EC2_DefaultRole"

  user_data = <<-EOF
    #!/bin/bash
    sudo yum update -y
    sudo yum install -y git wget java-17-amazon-corretto

    wget https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
    sudo tar -xvzf apache-maven-3.9.9-bin.tar.gz -C /opt
    sudo mv /opt/apache-maven-3.9.9 /opt/maven
    echo "export M2_HOME=/opt/maven" | sudo tee /etc/profile.d/maven.sh
    echo "export PATH=\$M2_HOME/bin:\$PATH" | sudo tee -a /etc/profile.d/maven.sh
    source /etc/profile.d/maven.sh

    git clone https://github.com/CreamsCode/neo4j-graph-loader /home/ec2-user/graph-api
    cd /home/ec2-user/graph-api

    export HAZELCAST_IP="${var.hazelcast_ip}"
    export NEO4J_USER=$"neo4j"
    export NEO4J_PASSWORD=$"neo4j"
    export NEO4J_IP="${aws_instance.neo4j_server.public_ip}"
    export NEO4J_URI="bolt://$NEO4J_IP:7687"

    echo "HAZELCAST_IP: $HAZELCAST_IP"
    echo "NEO4J_USER: $NEO4J_USER"
    echo "NEO4J_PASSWORD: $NEO4J_PASSWORD"
    echo "NEO4J_IP: $NEO4J_IP"
    echo "NEO4J_URI: $NEO4J_URI"

    sudo /opt/maven/bin/mvn clean install
    sudo java -DNEO4J_URI=$NEO4J_URI -DNEO4J_USER=$NEO4J_USER -DNEO4J_PASSWORD=$NEO4J_PASSWORD -jar API/target/API-1.0.jar
    echo "API server is running..."
  EOF

  tags = {
    Name = "API-Server"
  }
}

