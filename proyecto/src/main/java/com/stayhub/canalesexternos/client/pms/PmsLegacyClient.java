package com.stayhub.canalesexternos.client.pms;
import com.stayhub.canalesexternos.dto.*;
import java.util.List;
public interface PmsLegacyClient { RespuestaPms sincronizar(Long hotelId, List<DisponibilidadDTO> disponibilidad, List<TarifaDTO> tarifas); }
