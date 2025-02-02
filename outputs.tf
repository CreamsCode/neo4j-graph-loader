output "neo4j_public_ip" {
  value = aws_instance.neo4j_server.public_ip
}

output "neo4j_uri" {
  value = "bolt://${aws_instance.neo4j_server.public_ip}:7687"
}

output "graph_loader_public_ip" {
  value = aws_instance.graph_loader
}

output "api_endpoint" {
  value = "http://${aws_instance.api_server.public_ip}:8080/api"
}
