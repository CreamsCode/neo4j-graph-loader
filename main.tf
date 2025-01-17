provider "aws" {
  region = "us-east-1"
}

# VPC
resource "aws_vpc" "neo4j_vpc" {
  cidr_block = "10.0.0.0/16"
  tags = {
    Name = "DatamartVPC"
  }
}

# Subnet Pública
resource "aws_subnet" "neo4j_subnet" {
  vpc_id                  = aws_vpc.neo4j_vpc.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "us-east-1a"
  tags = {
    Name = "Neo4JSubnet"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "neo4j_igw" {
  vpc_id = aws_vpc.neo4j_vpc.id
  tags = {
    Name = "Neo4JInternetGateway"
  }
}

# Route Table para la Subnet Pública
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

# Asociar la Route Table a la Subnet Pública
resource "aws_route_table_association" "neo4j_subnet_association" {
  subnet_id      = aws_subnet.neo4j_subnet.id
  route_table_id = aws_route_table.neo4j_route_table.id
}

# Security Group
resource "aws_security_group" "neo4j_sg" {
  name        = "neo4j-sg"
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


# Instancia EC2
resource "aws_instance" "neo4j_instance" {
  ami           = "ami-05576a079321f21f8" # Amazon Linux 2 AMI
  instance_type = "t2.micro"
  subnet_id     = aws_subnet.neo4j_subnet.id
  key_name      = "vockey"
  security_group_names = [aws_security_group.neo4j_sg.name]

  user_data = <<-EOF
    #!/bin/bash
    sudo yum update -y
    sudo yum install -y java-17-amazon-corretto wget

    # Instalar Neo4J
    wget -O - https://debian.neo4j.com/neotechnology.gpg.key | sudo apt-key add -
    echo 'deb https://debian.neo4j.com stable 4.x' | sudo tee /etc/yum.repos.d/neo4j.list
    sudo yum update -y
    sudo yum install -y neo4j

    # Configurar Neo4J
    sudo sed -i 's/#dbms.default_listen_address=0.0.0.0/dbms.default_listen_address=0.0.0.0/' /etc/neo4j/neo4j.conf
    sudo sed -i 's/#dbms.default_advertised_address=localhost/dbms.default_advertised_address=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)/' /etc/neo4j/neo4j.conf

    # Iniciar Neo4J
    sudo systemctl enable neo4j
    sudo systemctl start neo4j
  EOF

  tags = {
    Name = "Neo4J-Instance"
  }
}


