import logging

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.requests import Request
from fastapi.responses import JSONResponse

from app.api.routes.analyze_router import router as analyze_router
from app.core.config import settings


logger = logging.getLogger("uvicorn.error")

app = FastAPI(title=settings.app_name)
app.include_router(analyze_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    body = await request.body()
    preview = body[:500].decode("utf-8", errors="replace")
    logger.warning("Request validation failed path=%s errors=%s body=%s", request.url.path, exc.errors(), preview)
    return JSONResponse(status_code=422, content={"detail": exc.errors()})
