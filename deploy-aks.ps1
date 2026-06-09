param(
    [string]$SubscriptionName = "Azure subscription 1",
    [string]$ResourceGroup = "demoResourceGroup",
    [string]$ClusterName = "DevCluster",
    [string]$AcrName = "etbArtifactory",
    [string]$ImageName = "demo-backend",
    [string]$ImageTag = "",
    [string]$Namespace = "default"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ImageTag)) {
    $ImageTag = "aks-" + (Get-Date -Format "yyyyMMdd-HHmmss")
}

$acrLoginServer = "$($AcrName.ToLower()).azurecr.io"
$fullImage = "$acrLoginServer/$ImageName`:$ImageTag"

Write-Host "==> Login (device code)"
az login --use-device-code | Out-Null

Write-Host "==> Set subscription: $SubscriptionName"
az account set --subscription $SubscriptionName

Write-Host "==> Build + push ARM64 image to ACR: $fullImage"
az acr build `
  --registry $AcrName `
  --platform linux/arm64 `
  --image "$ImageName`:$ImageTag" `
  --file Dockerfile `
  .

Write-Host "==> Get AKS credentials"
az aks get-credentials --resource-group $ResourceGroup --name $ClusterName --overwrite-existing

Write-Host "==> Ensure AKS can pull from ACR"
az aks update --resource-group $ResourceGroup --name $ClusterName --attach-acr $AcrName | Out-Null

Write-Host "==> Ensure DB secret exists (H2 demo defaults)"
kubectl -n $Namespace create secret generic demo-backend-db `
  --from-literal=DB_URL='jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' `
  --from-literal=DB_USERNAME='sa' `
  --from-literal=DB_PASSWORD='' `
  --dry-run=client -o yaml | kubectl apply -f -

Write-Host "==> Apply Kubernetes manifests"
kubectl -n $Namespace apply -f .\k8s\service.yaml
kubectl -n $Namespace apply -f .\k8s\deployment.yaml

Write-Host "==> Set deployment image"
kubectl -n $Namespace set image deployment/demo-backend demo-backend=$fullImage

Write-Host "==> Wait for rollout"
kubectl -n $Namespace rollout status deployment/demo-backend --timeout=300s

Write-Host "==> Current status"
kubectl -n $Namespace get deployment demo-backend
kubectl -n $Namespace get pods -l app=demo-backend
kubectl -n $Namespace get svc demo-backend

Write-Host ""
Write-Host "Done. For Postman access (ClusterIP service), run:"
Write-Host "kubectl -n $Namespace port-forward svc/demo-backend 8080:80"
Write-Host "Then use: http://localhost:8080"
